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

    private void applyDiscount(Product product) {
        if (product == null) return;
        java.math.BigDecimal discount = membershipLevelService.getCurrentUserDiscount();
        if (discount.compareTo(java.math.BigDecimal.ONE) < 0) {
            product.setOriginalPrice(product.getPrice());
            if (product.getPrice() != null) {
                product.setPrice(product.getPrice().multiply(discount));
            }
            product.setUserDiscount(discount);
            product.setUserLevelName(membershipLevelService.getCurrentUserLevelName());
            if (product.getSkus() != null) {
                for (ProductSku sku : product.getSkus()) {
                    if (sku.getPrice() != null) {
                        sku.setPrice(sku.getPrice().multiply(discount));
                    }
                    sku.setUserDiscount(discount);
                }
            }
        } else {
            product.setUserDiscount(java.math.BigDecimal.ONE);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)   // 主表+子表：任一步抛异常就整体回滚
    public void createProduct(Product product) {
        // 1) 归属校验：商家只能给自己的店加商品；ADMIN 放行
        ownershipChecker.assertShopOwned(product.getShopId());
        // 2) 两类商品：type=1 宠物 → 库存恒为 1、无多规格
        if (product.getType() != null && product.getType() == 1) {
            product.setStock(1);
            product.setSkus(null);
        }
        // 3) 默认值兜底
        if (product.getStatus() == null) product.setStatus(1);
        if (product.getSales() == null) product.setSales(0);
        product.setId(null);
        // 4) 先存主表（save 回填 id）
        this.save(product);
        // 5) 再存子表（绑定刚生成的 productId）
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
        Product product = this.getById(id);
        if (product == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        // 反向组装：查出「属于该商品」的所有 SKU 塞回去（条件是 product_id，不是 id）
        List<ProductSku> skus = productSkuMapper.selectList(
                new QueryWrapper<ProductSku>().eq("product_id", id));
        product.setSkus(skus);
        applyDiscount(product);
        return product;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProduct(Product product) {
        // 按「商品真实归属」校验：防止商家传自己的 shopId 却改别人的商品（越权）
        ownershipChecker.assertProductOwned(product.getId());
        product.setShopId(null);   // 不允许通过修改接口把商品挪到别的店
        
        if (product.getType() != null && product.getType() == 1) {
            product.setStock(1);
            product.setSkus(null);
        }

        boolean ok = this.updateById(product);
        if (!ok) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }

        // 全量替换 SKU：先删后插
        productSkuMapper.delete(new QueryWrapper<ProductSku>().eq("product_id", product.getId()));
        
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
        // 条件式：值为 null/空 时该条件不生效（前台一般只传 status=1）

        // MERCHANT 只看自己名下店铺的商品；ADMIN/null 不过滤
        // 若 query 已指定 shopId，取交集（MERCHANT 只能查自己店铺的）
        List<Long> shopIds = ownershipChecker.myShopIds();
        if (shopIds != null && !shopIds.isEmpty()) {
            if (query.getShopId() != null) {
                // MERCHANT 指定了 shopId → 必须在名下店铺范围内
                if (!shopIds.contains(query.getShopId())) {
                    return new PageResult<>(); // 越权查别人店铺 → 返回空页
                }
                w.eq(Product::getShopId, query.getShopId());
            } else {
                w.in(Product::getShopId, shopIds);
            }
        } else if (shopIds != null) {
            // MERCHANT 无店铺 → 返回空
            return new PageResult<>();
        } else {
            w.eq(query.getShopId() != null, Product::getShopId, query.getShopId());
        }

        // 多门店 IN 过滤（商家后台用，逗号分隔的 shopIds）
        if (query.getShopId() == null && StringUtils.hasText(query.getShopIds())) {
            java.util.List<Long> ids = new java.util.ArrayList<>();
            for (String s : query.getShopIds().split(",")) {
                try { ids.add(Long.parseLong(s.trim())); } catch (NumberFormatException ignored) {}
            }
            if (!ids.isEmpty()) {
                w.in(Product::getShopId, ids);
            }
        }

        w.eq(query.getCategoryId() != null, Product::getCategoryId, query.getCategoryId());

        w.like(StringUtils.hasText(query.getName()), Product::getName, query.getName());

        w.eq(query.getType() != null, Product::getType, query.getType());

        w.eq(query.getStatus() != null, Product::getStatus, query.getStatus());

        w.ge(query.getMinPrice() != null, Product::getPrice, query.getMinPrice());
        w.le(query.getMaxPrice() != null, Product::getPrice, query.getMaxPrice());

        if ("sales_desc".equals(query.getSort())) {
            w.orderByDesc(Product::getSales);
        } else if ("price_asc".equals(query.getSort())) {
            w.orderByAsc(Product::getPrice);
        } else if ("price_desc".equals(query.getSort())) {
            w.orderByDesc(Product::getPrice);
        } else {
            w.orderByDesc(Product::getCreateTime);
        }

        Page<Product> pageInfo = this.page(query.toPage(), w);
        if (pageInfo.getRecords() != null) {
            for (Product p : pageInfo.getRecords()) {
                applyDiscount(p);
            }
        }
        return PageResult.of(pageInfo);
    }

    @Override
    public List<Product> homeProducts(String strategy, Integer limit) {
        // limit 兜底 + 上限，防止前端传 0 或超大值
        int n = (limit == null || limit <= 0) ? 6 : Math.min(limit, 50);

        if ("RECOMMEND".equalsIgnoreCase(strategy)) {
            return recommendProducts(n);
        }

        LambdaQueryWrapper<Product> w = new LambdaQueryWrapper<>();
        w.eq(Product::getStatus, 1);   // 首页只展示「上架」商品
        boolean isCfFallback = false;
        if ("NEW".equalsIgnoreCase(strategy)) {
            w.orderByDesc(Product::getCreateTime);   // 新鲜上架
        } else if ("CF".equalsIgnoreCase(strategy)) {
            Long userId = UserContext.getUserId();
            if (userId != null) {
                // 去 recommend_result 里找该用户的推荐商品 ID
                List<Long> productIds = jdbcTemplate.queryForList(
                        "SELECT product_id FROM recommend_result WHERE user_id = ? ORDER BY score DESC LIMIT ?",
                        Long.class, userId, n
                );
                if (!productIds.isEmpty()) {
                    w.in(Product::getId, productIds);
                    // 确保按 in 的顺序或至少不报错，先简单返回这些数据
                    // Mybatis Plus 的 in 会打乱顺序，如果不介意这里就直接返回
                    List<Product> list = this.list(w);
                    if (list == null) {
                        list = new java.util.ArrayList<>();
                    }
                    
                    // Step 4 凑数补齐逻辑：如果离线协同过滤推荐数量不足 n 条，去重后用全站错峰销量榜（第2页）凑满
                    if (list.size() < n) {
                        LambdaQueryWrapper<Product> hotW = new LambdaQueryWrapper<>();
                        hotW.eq(Product::getStatus, 1).orderByDesc(Product::getSales);
                        List<Product> hots = getPageWithFallbackProtection(hotW, n + list.size(), 2);
                        fillWithDeduplication(list, n, hots);
                    }
                    
                    for (Product p : list) {
                        applyDiscount(p);
                    }
                    return list;
                }
            }
            // 兜底：未登录或该用户没有跑过 CF 推荐，错峰取全站销量榜第 2 页，避开第一页的热榜
            isCfFallback = true;
            w.orderByDesc(Product::getSales);
        } else {
            // HOT / 默认 → 按销量
            w.orderByDesc(Product::getSales);
        }
        // 取前 N 条：如果是 CF 兜底，错峰取第 2 页避开第一页的热榜；同时应用智能防破窗保护
        return getPageWithFallbackProtection(w, n, isCfFallback ? 2 : 1);
    }

    private List<Product> recommendProducts(int n) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            // 错峰兜底：未登录访客取 NEW（最新上架）的第 2 页，避开首屏第 1 页的上新榜；同时带智能防破窗
            LambdaQueryWrapper<Product> w = new LambdaQueryWrapper<>();
            w.eq(Product::getStatus, 1).orderByDesc(Product::getCreateTime);
            return getPageWithFallbackProtection(w, n, 2);
        }

        // 1. 从 Redis 取出用户画像中权重最高的 Top 3 标签
        String redisKey = "user_profile:" + userId + ":tags";
        java.util.Set<String> tagIdsStr = stringRedisTemplate.opsForZSet().reverseRange(redisKey, 0, 2);
        
        List<Product> recommendList = new java.util.ArrayList<>();
        
        if (tagIdsStr != null && !tagIdsStr.isEmpty()) {
            List<Long> tagIds = new java.util.ArrayList<>();
            for (String s : tagIdsStr) {
                tagIds.add(Long.parseLong(s));
            }
            
            // 2. 根据这几个标签去 product_tag 找对应的 product_id
            List<Long> productIds = productTagMapper.selectProductIdsByTagIds(tagIds);
            
            if (productIds != null && !productIds.isEmpty()) {
                // 3. 从数据库查出这些商品，必须是上架的 (status=1)
                LambdaQueryWrapper<Product> w = new LambdaQueryWrapper<>();
                w.eq(Product::getStatus, 1).in(Product::getId, productIds).orderByDesc(Product::getSales);
                // 限制最多取 n 条
                List<Product> records = this.page(new Page<>(1, n), w).getRecords();
                if (records != null) {
                    recommendList = new java.util.ArrayList<>(records);
                }
            }
        }

        // 4. 如果标签推荐出来的数量不够，用最新商品错峰凑数 (冷启动/新用户)
        if (recommendList.size() < n) {
            LambdaQueryWrapper<Product> w = new LambdaQueryWrapper<>();
            w.eq(Product::getStatus, 1).orderByDesc(Product::getCreateTime);
            List<Product> hots = getPageWithFallbackProtection(w, n + recommendList.size(), 2);
            fillWithDeduplication(recommendList, n, hots);
        }

        if (recommendList != null) {
            for (Product p : recommendList) {
                applyDiscount(p);
            }
        }
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
            for (Product p : list) {
                applyDiscount(p);
            }
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public java.math.BigDecimal checkPriceAndDeductStock(Long productId, Long skuId, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "购买数量必须大于0");
        }
        Product product = this.getById(productId);
        if (product == null || product.getStatus() != 1) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "商品不存在或已下架");
        }

        if (skuId != null) {
            // 有 SKU
            ProductSku sku = productSkuMapper.selectById(skuId);
            if (sku == null || !sku.getProductId().equals(productId)) {
                throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "商品规格不存在");
            }
            if (sku.getStock() < quantity) {
                throw new BusinessException(ResultCode.ERROR.getCode(), "商品规格库存不足");
            }
            // 乐观锁思想扣减库存
            int updated = productSkuMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ProductSku>()
                    .setSql("stock = stock - " + quantity)
                    .eq(ProductSku::getId, skuId)
                    .ge(ProductSku::getStock, quantity));
            if (updated == 0) {
                throw new BusinessException(ResultCode.ERROR.getCode(), "库存扣减失败，已被抢空请重试");
            }
            return sku.getPrice();
        } else {
            // 无 SKU，扣减主表库存
            if (product.getStock() < quantity) {
                throw new BusinessException(ResultCode.ERROR.getCode(), "商品库存不足");
            }
            int updated = this.baseMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Product>()
                    .setSql("stock = stock - " + quantity)
                    .eq(Product::getId, productId)
                    .ge(Product::getStock, quantity));
            if (updated == 0) {
                throw new BusinessException(ResultCode.ERROR.getCode(), "库存扣减失败，已被抢空请重试");
            }
            return product.getPrice();
        }
    }

    @Override
    public List<Product> getAllActiveProductsForRecommend() {
        LambdaQueryWrapper<Product> w = new LambdaQueryWrapper<>();
        w.eq(Product::getStatus, 1);
        w.select(Product::getId, Product::getCategoryId, Product::getName, Product::getSales);
        return this.list(w);
    }
}
