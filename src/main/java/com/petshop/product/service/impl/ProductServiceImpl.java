package com.petshop.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.petshop.common.BusinessException;
import com.petshop.common.PageResult;
import com.petshop.common.ResultCode;
import com.petshop.product.entity.Product;
import com.petshop.product.entity.ProductSku;
import com.petshop.product.mapper.ProductMapper;
import com.petshop.product.mapper.ProductSkuMapper;
import com.petshop.product.mapper.ProductTagMapper;
import com.petshop.product.service.ProductPageQuery;
import com.petshop.product.service.ProductService;
import com.petshop.security.OwnershipChecker;
import com.petshop.security.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.math.BigDecimal;

import java.util.List;

/**
 * 商品 Service 实现。
 * 创建商品要同时写 product 主表 + product_sku 子表（一个事务）；详情要反向把 SKU 组装回去。
 */
@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product>
        implements ProductService {

    @Autowired
    private OwnershipChecker ownershipChecker;
    @Autowired
    private ProductSkuMapper productSkuMapper;
    @Autowired
    private ProductTagMapper productTagMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired
    private com.petshop.user.service.MembershipLevelService membershipLevelService;
    @Autowired
    private com.petshop.shop.mapper.ShopMapper shopMapper;
    @Autowired
    private com.petshop.recommend.service.RecommendRankService recommendRankService;

    /**
     * 【内部工具】：动态计算商品的会员折后价。
     * 由于同一个商品在不同等级的用户眼里看到的价格是不一样的，所以数据库只存“原价”。
     * 当商品被查询出来准备返回给前端展示时，临时用这个方法计算打折后的真实价格。
     * 如果用户有折扣，会把原价暂存到 originalPrice 字段，方便前端做带划线的“原价展示”。
     */
    private void applyDiscount(Product product, BigDecimal discount, String levelName) {
        if (product == null) return;
        if (discount == null) discount =BigDecimal.ONE;
        if (discount.compareTo(BigDecimal.ONE) < 0) {
            product.setOriginalPrice(product.getPrice());
            if (product.getPrice() != null) {
                product.setPrice(product.getPrice().multiply(discount));
            }
            product.setUserDiscount(discount);
            product.setUserLevelName(levelName);
            if (product.getSkus() != null) {
                for (ProductSku sku : product.getSkus()) {
                    if (sku.getPrice() != null) {
                        sku.setPrice(sku.getPrice().multiply(discount));
                    }
                    sku.setUserDiscount(discount);
                }
            } 
        } else {
            product.setUserDiscount(BigDecimal.ONE);
        }
    }

    /**
     * 【内部工具】：批量补全商品的店铺名称。
     * 商品表（product）里只存了 shop_id。为了减少数据库连表查询（JOIN）的压力，
     * 我们在代码层面上把查出来的一批商品的 shop_id 收集起来，再去店铺表（shop）里批量查名字，
     * 最后再把名字一个个对应着塞回商品对象里（如果没有挂载店铺，默认显示为“宠物商城自营”）。
     */
    private void fillShopNames(List<Product> products) {
        if (products == null || products.isEmpty()) return;
        List<Long> shopIds = products.stream()
                .map(Product::getShopId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        java.util.Map<Long, String> shopNameMap = new java.util.HashMap<>();
        if (!shopIds.isEmpty()) {
            List<com.petshop.shop.entity.Shop> shops = shopMapper.selectBatchIds(shopIds);
            for (com.petshop.shop.entity.Shop s : shops) {
                if (s != null && s.getName() != null && !s.getName().trim().isEmpty()) {
                    shopNameMap.put(s.getId(), s.getName());
                }
            }
        }
        for (Product p : products) {
            if (p.getShopId() != null && shopNameMap.containsKey(p.getShopId())) {
                p.setShopName(shopNameMap.get(p.getShopId()));
            } else {
                p.setShopName("宠物商城自营");
            }
        }
    }

    /**
     * 【内部工具】：商品查询的最终后处理。
     * 每次从数据库里分页或者单条查出商品后，必须调用这个统一的方法，
     * 把“千人千面的会员折扣价”和“店铺名称”这两个必须要在展示时才算得出来的动态属性组装进去。
     */
    private void applyDiscountAndShopName(List<Product> list) {
        if (list == null || list.isEmpty()) return;
        BigDecimal discount = membershipLevelService.getCurrentUserDiscount();
        String levelName = membershipLevelService.getCurrentUserLevelName();
        for (Product p : list) {
            applyDiscount(p, discount, levelName);
        }
        fillShopNames(list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)   // 主表+子表：任一步抛异常就整体回滚
    public void createProduct(Product product) {
        // 1) 权限校验：防越权，商家绝不能把商品建在别人家的店铺里
        ownershipChecker.assertShopOwned(product.getShopId());
        
        // 2) 宠物活体特殊处理：(type=1 活体宠物) 库存永远只有 1 只，且绝对没有 SKU（宠物不能选大中小号）
        if (product.getType() != null && product.getType() == 1) {
            product.setStock(1);
            product.setSkus(null);
        }
        
        // 3) 设置默认值兜底：新上架商品默认生效(1)，初始销量为 0
        if (product.getStatus() == null) product.setStatus(1);
        if (product.getSales() == null) product.setSales(0);
        product.setId(null); // 防止前端恶作剧传ID导致覆盖别人的商品
        
        // 4) 第一步入库：先保存商品主表（MyBatis-Plus 会自动把刚生成的自增 ID 填回到 product 对象里）
        this.save(product);
        
        // 5) 第二步入库：再保存商品的 SKU 子表（把刚才拿到的主表 ID 绑定到每一个 SKU 上，形成父子关联）
        List<ProductSku> skus = product.getSkus();
        if (skus != null && !skus.isEmpty()) {
            for (ProductSku sku : skus) {
                sku.setId(null);
                sku.setProductId(product.getId());
                productSkuMapper.insert(sku);
            }
        }
    }

    @Override
    public Product getProductById(Long id) {
        // 1) 先查出商品的主表基本信息
        Product product = this.getById(id);
        if (product == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        
        // 2) 【反向组装模式】：因为数据库是拆开存的，所以这里要根据主表 ID 去子表捞出所有的 SKU（规格），手动塞回商品对象里
        // 注意：条件是 product_id = id，而不是直接查 sku.id
        List<ProductSku> skus = productSkuMapper.selectList(
                new QueryWrapper<ProductSku>().eq("product_id", id));
        product.setSkus(skus);
        
        // 3) 为这件商品动态计算“当前登录用户的会员折后价”，并补充完整的店铺名称（为了前台漂亮地展示）
        applyDiscountAndShopName(java.util.Collections.singletonList(product));
        
        return product;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProduct(Product product) {
        // 1) 防越权校验：必须根据【商品本身的真实归属】来校验，严防恶意商家通过修改接口把别人的商品挂到自己店里
        ownershipChecker.assertProductOwned(product.getId());
        product.setShopId(null);   // 强行把 shopId 设为空，防止修改操作发生店铺转移
        
        // 2) 宠物活体特殊处理（同创建逻辑：单只活物无多规格）
        if (product.getType() != null && product.getType() == 1) {
            product.setStock(1);
            product.setSkus(null);
        }

        // 3) 先更新主表基本信息
        boolean ok = this.updateById(product);
        if (!ok) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }

        // 4) 【暴力全量替换 SKU 模式】：为了防止前台传来的 SKU 列表错乱（增删改混杂），
        // 最简单可靠的办法就是：先把这件商品旧的 SKU 全删光！
        productSkuMapper.delete(new QueryWrapper<ProductSku>().eq("product_id", product.getId()));
        
        // 5) 然后再把前端传过来的最新 SKU 列表作为全新的数据，一条条重新插进去
        List<ProductSku> skus = product.getSkus();
        if (skus != null && !skus.isEmpty()) {
            for (ProductSku sku : skus) {
                sku.setId(null);
                sku.setProductId(product.getId());
                productSkuMapper.insert(sku);
            }
        }
    }

    @Override
    public void deleteProduct(Long id) {
        // id 是商品 id，用 assertProductOwned（不是 assertShopOwned）
        ownershipChecker.assertProductOwned(id);
        boolean ok = this.removeById(id);   // @TableLogic → 逻辑删除
        if (!ok) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
    }

    @Override
    public PageResult<Product> pageProducts(ProductPageQuery query) {
        LambdaQueryWrapper<Product> w = new LambdaQueryWrapper<>();

        // MERCHANT 只看自己名下店铺的商品；ADMIN/null 不过滤
        if (applyShopFilter(w, query)) {
            return new PageResult<>(); // 越权或无店铺 → 返回空页
        }

        // 多门店 IN 过滤（商家后台用，逗号分隔的 shopIds）
        applyMultiShopFilter(w, query);

        // 条件式：值为 null/空 时该条件不生效
        w.eq(query.getCategoryId() != null, Product::getCategoryId, query.getCategoryId());
        w.like(StringUtils.hasText(query.getName()), Product::getName, query.getName());
        w.eq(query.getType() != null, Product::getType, query.getType());
        w.eq(query.getStatus() != null, Product::getStatus, query.getStatus());
        w.ge(query.getMinPrice() != null, Product::getPrice, query.getMinPrice());
        w.le(query.getMaxPrice() != null, Product::getPrice, query.getMaxPrice());

        applySortOrder(w, query);

        Page<Product> pageInfo = this.page(query.toPage(), w);
        if (pageInfo.getRecords() != null) {
            applyDiscountAndShopName(pageInfo.getRecords());
        }
        return PageResult.of(pageInfo);
    }

    /**
     * 应用店铺归属过滤。
     * @return true 表示应直接返回空页（越权或商家无店铺）
     */
    private boolean applyShopFilter(LambdaQueryWrapper<Product> w, ProductPageQuery query) {
        List<Long> shopIds = ownershipChecker.myShopIds();
        if (shopIds != null && !shopIds.isEmpty()) {
            if (query.getShopId() != null) {
                if (!shopIds.contains(query.getShopId())) {
                    return true;
                }
                w.eq(Product::getShopId, query.getShopId());
            } else {
                w.in(Product::getShopId, shopIds);
            }
        } else if (shopIds != null) {
            return true;
        } else {
            w.eq(query.getShopId() != null, Product::getShopId, query.getShopId());
        }
        return false;
    }

    /** 多门店逗号分隔 shopIds 过滤 */
    private void applyMultiShopFilter(LambdaQueryWrapper<Product> w, ProductPageQuery query) {
        if (query.getShopId() != null || !StringUtils.hasText(query.getShopIds())) {
            return;
        }
        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (String s : query.getShopIds().split(",")) {
            try { ids.add(Long.parseLong(s.trim())); } catch (NumberFormatException ignored) { /* non-numeric shopId token skipped */ }
        }
        if (!ids.isEmpty()) {
            w.in(Product::getShopId, ids);
        }
    }

    /** 应用排序规则 */
    private void applySortOrder(LambdaQueryWrapper<Product> w, ProductPageQuery query) {
        String sort = query.getSort();
        if ("sales_desc".equals(sort)) {
            w.orderByDesc(Product::getSales);
        } else if ("price_asc".equals(sort)) {
            w.orderByAsc(Product::getPrice);
        } else if ("price_desc".equals(sort)) {
            w.orderByDesc(Product::getPrice);
        } else if ("new".equals(sort)) {
            w.orderByDesc(Product::getCreateTime);
        } else if ("recommend".equals(sort)) {
            applyRecommendSort(w, query);
        } else {
            w.orderByDesc(Product::getCreateTime);
        }
    }

    /** 推荐排序：基于用户画像标签的个性化推荐，无推荐结果则按销量降序 */
    private void applyRecommendSort(LambdaQueryWrapper<Product> w, ProductPageQuery query) {
        List<Long> recIds = getRecommendProductIds(query.toPage().getSize() * 2);
        if (!recIds.isEmpty()) {
            String idsStr = recIds.stream().map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(","));
            w.last("ORDER BY FIELD(id," + idsStr + ") DESC, sales DESC");
        } else {
            w.orderByDesc(Product::getSales);
        }
    }

    @Override
    public List<Product> homeProducts(String strategy, Integer limit) {
        // limit 兜底 + 上限，防止前端传 0 或超大值
        int n = (limit == null || limit <= 0) ? 6 : Math.min(limit, 50);

        if ("RECOMMEND".equalsIgnoreCase(strategy)) {
            return recommendProducts(n);
        }
        if ("NEW".equalsIgnoreCase(strategy)) {
            return homeNewProducts(n);
        }
        if ("CF".equalsIgnoreCase(strategy)) {
            return homeCfProducts(n);
        }
        // HOT / 默认 → 按销量
        LambdaQueryWrapper<Product> w = new LambdaQueryWrapper<>();
        w.eq(Product::getStatus, 1).orderByDesc(Product::getSales);
        return getPageWithFallbackProtection(w, n, 1);
    }

    /** 首页「最新上架」策略 */
    private List<Product> homeNewProducts(int n) {
        LambdaQueryWrapper<Product> w = new LambdaQueryWrapper<>();
        w.eq(Product::getStatus, 1).orderByDesc(Product::getCreateTime);
        return getPageWithFallbackProtection(w, n, 1);
    }

    /** 首页「协同过滤推荐」策略：登录用户取 CF 推荐结果，未命中则错峰兜底 */
    private List<Product> homeCfProducts(int n) {
        Long userId = UserContext.getUserId();
        if (userId != null) {
            List<Product> cfResult = fetchCfRecommendations(userId, n);
            if (cfResult != null) {
                return cfResult;
            }
        }
        // 兜底：未登录或该用户没有跑过 CF 推荐，错峰取全站销量榜第 2 页
        LambdaQueryWrapper<Product> w = new LambdaQueryWrapper<>();
        w.eq(Product::getStatus, 1).orderByDesc(Product::getSales);
        return getPageWithFallbackProtection(w, n, 2);
    }

    /** 从 recommend_result 查询 CF 推荐商品，不足时凑数；无数据时返回 null 表示走兜底 */
    private List<Product> fetchCfRecommendations(Long userId, int n) {
        List<Long> productIds = jdbcTemplate.queryForList(
                "SELECT product_id FROM recommend_result WHERE user_id = ? ORDER BY score DESC LIMIT ?",
                Long.class, userId, n);
        if (productIds.isEmpty()) {
            return null;
        }
        LambdaQueryWrapper<Product> w = new LambdaQueryWrapper<>();
        w.eq(Product::getStatus, 1).in(Product::getId, productIds);
        List<Product> list = this.list(w);
        if (list == null) {
            list = new java.util.ArrayList<>();
        }
        // 凑数补齐：如果离线协同过滤推荐数量不足 n 条，去重后用全站错峰销量榜凑满
        if (list.size() < n) {
            LambdaQueryWrapper<Product> hotW = new LambdaQueryWrapper<>();
            hotW.eq(Product::getStatus, 1).orderByDesc(Product::getSales);
            List<Product> hots = getPageWithFallbackProtection(hotW, n + list.size(), 2);
            fillWithDeduplication(list, n, hots);
        }
        applyDiscountAndShopName(list);
        return list;
    }

    private List<Product> recommendProducts(int n) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            // 错峰兜底：未登录访客取 NEW（最新上架）的第 2 页，避开首屏第 1 页的上新榜；同时带智能防破窗
            LambdaQueryWrapper<Product> w = new LambdaQueryWrapper<>();
            w.eq(Product::getStatus, 1).orderByDesc(Product::getCreateTime);
            return getPageWithFallbackProtection(w, n, 2);
        }

        // 多路召回 + 融合排序（CF + 标签画像 + 宠物档案 + 人群热度，含冲突过滤/近购惩罚/类目打散）
        List<Product> recommendList = new java.util.ArrayList<>();
        try {
            recommendList = recommendRankService.rankForUser(userId, n);
        } catch (Exception e) {
            // 排序服务异常不影响首页，走兜底
        }

        // 信号不足（新用户无行为无档案）：用最新商品错峰凑数
        if (recommendList.size() < n) {
            LambdaQueryWrapper<Product> w = new LambdaQueryWrapper<>();
            w.eq(Product::getStatus, 1).orderByDesc(Product::getCreateTime);
            List<Product> news = getPageWithFallbackProtection(w, n + recommendList.size(), 2);
            fillWithDeduplication(recommendList, n, news);
        }

        applyDiscountAndShopName(recommendList);
        return recommendList;
    }

    /**
     * 智能错峰分页查询（带防破窗降级保护机制）
     * @param w 查询条件
     * @param n 每页数量
     * @param pageNum 目标页码（错峰用，例如传 2）
     */
    private List<Product> getPageWithFallbackProtection(LambdaQueryWrapper<Product> w, int n, int pageNum) {
        List<Product> list = this.page(new Page<>(pageNum, n), w).getRecords();
        // 智能防破窗：如果错峰第 N 页（pageNum > 1）数据为空或未拿够，说明数据库商品总数较少，优雅回退到第 1 页
        if ((list == null || list.isEmpty()) && pageNum > 1) {
            list = this.page(new Page<>(1, n), w).getRecords();
        }
        if (list != null) {
            applyDiscountAndShopName(list);
        }
        return list != null ? list : new java.util.ArrayList<>();
    }

    /**
     * 推荐结果去重凑数补齐通用方法
     * @param targetList 当前已有的推荐商品列表（需要补齐的目标集合）
     * @param targetSize 期望达到的总条数 n
     * @param fallbackList 用于凑数补齐的备选商品列表（错峰拉取的备用数据）
     */
    private void fillWithDeduplication(List<Product> targetList, int targetSize, List<Product> fallbackList) {
        if (targetList.size() >= targetSize || fallbackList == null || fallbackList.isEmpty()) {
            return;
        }
        int need = targetSize - targetList.size();
        for (Product candidate : fallbackList) {
            boolean exists = false;
            for (Product r : targetList) {
                if (r.getId().equals(candidate.getId())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                targetList.add(candidate);
                need--;
                if (need <= 0) break;
            }
        }
    }

    /**
     * 获取当前用户的推荐商品 ID 列表（用于分页排序）
     * 复用 Redis 用户画像标签推荐逻辑，仅返回 ID 列表
     */
    private List<Long> getRecommendProductIds(long limit) {
        Long userId = UserContext.getUserId();
        if (userId == null) return java.util.Collections.emptyList();

        List<Long> result = new java.util.ArrayList<>();
        // 1. 从 Redis 取用户画像 Top 3 标签对应的商品 ID
        collectTagBasedProductIds(userId, limit, result);
        // 2. 从 recommend_result 表补充 CF 推荐
        collectCfBasedProductIds(userId, limit, result);
        return result;
    }

    /** 根据用户画像标签从 product_tag 收集推荐商品 ID */
    private void collectTagBasedProductIds(Long userId, long limit, List<Long> result) {
        String redisKey = "user_profile:" + userId + ":tags";
        java.util.Set<String> tagIdsStr = stringRedisTemplate.opsForZSet().reverseRange(redisKey, 0, 2);
        if (tagIdsStr == null || tagIdsStr.isEmpty()) {
            return;
        }
        List<Long> tagIds = new java.util.ArrayList<>();
        for (String s : tagIdsStr) {
            try { tagIds.add(Long.parseLong(s)); } catch (NumberFormatException ignored) { /* non-numeric tag skipped */ }
        }
        if (!tagIds.isEmpty()) {
            List<Long> productIds = productTagMapper.selectProductIdsByTagIds(tagIds);
            if (productIds != null) {
                result.addAll(productIds.subList(0, (int) Math.min(productIds.size(), limit)));
            }
        }
    }

    /** 从 recommend_result 表补充 CF 推荐商品 ID（去重） */
    private void collectCfBasedProductIds(Long userId, long limit, List<Long> result) {
        if (result.size() >= limit) {
            return;
        }
        try {
            List<Long> cfIds = jdbcTemplate.queryForList(
                    "SELECT product_id FROM recommend_result WHERE user_id = ? ORDER BY score DESC LIMIT ?",
                    Long.class, userId, limit - result.size());
            for (Long id : cfIds) {
                if (!result.contains(id)) result.add(id);
            }
        } catch (Exception ignored) { /* recommend_result table may not exist yet */ }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal checkPriceAndDeductStock(Long productId, Long skuId, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "购买数量必须大于0");
        }
        Product product = this.getById(productId);
        if (product == null || product.getStatus() != 1) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "商品不存在或已下架");
        }

        if (skuId != null) {
            return deductSkuStock(productId, skuId, quantity);
        }
        return deductProductStock(product, quantity);
    }

    /** 有 SKU 时校验并扣减 SKU 库存，返回 SKU 单价 */
    private BigDecimal deductSkuStock(Long productId, Long skuId, int quantity) {
        ProductSku sku = productSkuMapper.selectById(skuId);
        if (sku == null || !sku.getProductId().equals(productId)) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "商品规格不存在");
        }
        if (sku.getStock() < quantity) {
            throw new BusinessException(ResultCode.ERROR.getCode(), "商品规格库存不足");
        }
        int updated = productSkuMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ProductSku>()
                        .setSql("stock = stock - " + quantity)
                        .eq(ProductSku::getId, skuId)
                        .ge(ProductSku::getStock, quantity));
        if (updated == 0) {
            throw new BusinessException(ResultCode.ERROR.getCode(), "库存扣减失败，已被抢空请重试");
        }
        return sku.getPrice();
    }

    /** 无 SKU 时校验并扣减主表库存，返回商品单价 */
    private BigDecimal deductProductStock(Product product, int quantity) {
        if (product.getStock() < quantity) {
            throw new BusinessException(ResultCode.ERROR.getCode(), "商品库存不足");
        }
        int updated = this.baseMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Product>()
                        .setSql("stock = stock - " + quantity)
                        .eq(Product::getId, product.getId())
                        .ge(Product::getStock, quantity));
        if (updated == 0) {
            throw new BusinessException(ResultCode.ERROR.getCode(), "库存扣减失败，已被抢空请重试");
        }
        return product.getPrice();
    }

    @Override
    public List<Product> getAllActiveProductsForRecommend() {
        LambdaQueryWrapper<Product> w = new LambdaQueryWrapper<>();
        w.eq(Product::getStatus, 1);
        w.select(Product::getId, Product::getCategoryId, Product::getName, Product::getSales);
        return this.list(w);
    }
}
