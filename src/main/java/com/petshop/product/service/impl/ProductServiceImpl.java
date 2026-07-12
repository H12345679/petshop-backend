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
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

import com.petshop.product.entity.ProductES;
import com.petshop.product.repository.ProductESRepository;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("homeAssemblyThreadPool")
    private java.util.concurrent.Executor homeAssemblyThreadPool;

    @Autowired
    private ProductESRepository productESRepository;
    @Autowired
    private ElasticsearchOperations elasticsearchOperations;
    @Autowired
    private com.petshop.ai.AiProvider aiProvider;

    private ProductES mapToES(Product product) {
        if (product == null) return null;
        ProductES es = new ProductES();
        es.setId(product.getId());
        es.setShopId(product.getShopId());
        es.setCategoryId(product.getCategoryId());
        es.setName(product.getName());
        es.setDescription(product.getDescription());
        es.setType(product.getType());
        es.setPrice(product.getPrice());
        es.setStock(product.getStock());
        es.setSales(product.getSales());
        es.setStatus(product.getStatus());
        if (product.getCreateTime() != null) {
            es.setCreateTime(java.util.Date.from(product.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant()));
        }

        // 提取商品文本生成 Embedding
        try {
            String textToEmbed = String.format("商品名称：%s,描述：%s", 
                product.getName() != null ? product.getName() : "", 
                product.getDescription() != null ? product.getDescription() : "");
            java.util.List<Double> embedding = aiProvider.getEmbedding(textToEmbed);
            es.setEmbedding(embedding);
        } catch (Exception e) {
            log.warn("生成商品 Embedding 失败: " + e.getMessage());
        }

        return es;
    }

    @Override
    public long syncAllToES() {
        // 先测试能否连通 ES（抛异常说明不可用）
        elasticsearchOperations.indexOps(IndexCoordinates.of("product")).exists();
        
        long totalSynced = 0;
        int current = 1;
        int size = 500; // 分批获取
        
        while (true) {
            Page<Product> page = this.page(new Page<>(current, size));
            List<Product> records = page.getRecords();
            if (records == null || records.isEmpty()) {
                break;
            }
            
            List<ProductES> esList = new ArrayList<>();
            for (Product p : records) {
                esList.add(mapToES(p));
            }
            if (!esList.isEmpty()) {
                productESRepository.saveAll(esList);
                totalSynced += esList.size();
            }
            
            if (current >= page.getPages()) {
                break;
            }
            current++;
        }
        return totalSynced;
    }

    /** 事务提交成功后同步更新 ES (防分布式幽灵数据) */
    private void syncToEsAfterCommit(Long productId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executeEsSync(productId);
                }
            });
        } else {
            executeEsSync(productId);
        }
    }

    private void executeEsSync(Long productId) {
        try {
            Product dbProduct = this.getById(productId);
            if (dbProduct != null) {
                productESRepository.save(mapToES(dbProduct));
            }
        } catch (Exception e) {
            log.error("同步修改商品(ID:" + productId + ")到 ES 失败", e);
        }
    }

    /** 事务提交成功后从 ES 移除 */
    private void deleteFromEsAfterCommit(Long productId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executeEsDelete(productId);
                }
            });
        } else {
            executeEsDelete(productId);
        }
    }

    private void executeEsDelete(Long productId) {
        try {
            productESRepository.deleteById(productId);
        } catch (Exception e) {
            log.error("从 ES 删除商品(ID:" + productId + ")失败", e);
        }
    }

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
        java.util.Map<Long, String> shopNameMap = buildShopNameMap(shopIds);
        for (Product p : products) {
            if (p.getShopId() != null && shopNameMap.containsKey(p.getShopId())) {
                p.setShopName(shopNameMap.get(p.getShopId()));
            } else {
                p.setShopName("宠物商城自营");
            }
        }
    }

    private java.util.Map<Long, String> buildShopNameMap(List<Long> shopIds) {
        java.util.Map<Long, String> shopNameMap = new java.util.HashMap<>();
        if (shopIds.isEmpty()) return shopNameMap;
        List<com.petshop.shop.entity.Shop> shops = shopMapper.selectBatchIds(shopIds);
        for (com.petshop.shop.entity.Shop s : shops) {
            if (s != null && s.getName() != null && !s.getName().trim().isEmpty()) {
                shopNameMap.put(s.getId(), s.getName());
            }
        }
        return shopNameMap;
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
            int totalStock = 0;
            for (ProductSku sku : skus) {
                sku.setId(null);
                sku.setProductId(product.getId());
                productSkuMapper.insert(sku);
                totalStock += (sku.getStock() != null ? sku.getStock() : 0);
            }
            // 有 SKU 时，主表 stock = 所有 SKU 库存之和
            this.baseMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Product>()
                            .eq(Product::getId, product.getId())
                            .set(Product::getStock, totalStock));
            product.setStock(totalStock); // 同步回对象以便发往 ES
        }
        
        // 6) 同步到 ES
        syncToEsAfterCommit(product.getId());
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

        // 4) 【平滑替换 SKU 模式】：比较新老 SKU 列表，增删改
        List<ProductSku> oldSkus = productSkuMapper.selectList(new QueryWrapper<ProductSku>().eq("product_id", product.getId()));
        List<ProductSku> newSkus = product.getSkus();
        if (newSkus == null) newSkus = new ArrayList<>();
        
        Map<Long, ProductSku> oldSkuMap = oldSkus.stream().collect(Collectors.toMap(ProductSku::getId, s -> s));
        int totalStock = 0;
        
        for (ProductSku newSku : newSkus) {
            if (newSku.getId() != null && oldSkuMap.containsKey(newSku.getId())) {
                // 更新现有 SKU
                newSku.setProductId(product.getId());
                productSkuMapper.updateById(newSku);
                oldSkuMap.remove(newSku.getId()); // 从老Map中移除，剩下的就是要删除的
            } else {
                // 插入新 SKU
                newSku.setId(null);
                newSku.setProductId(product.getId());
                productSkuMapper.insert(newSku);
            }
            totalStock += (newSku.getStock() != null ? newSku.getStock() : 0);
        }
        
        // 删除已经不存在的旧 SKU
        for (Long deleteSkuId : oldSkuMap.keySet()) {
            productSkuMapper.deleteById(deleteSkuId);
        }

        // 有 SKU 时，主表 stock = 所有 SKU 库存之和
        if (!newSkus.isEmpty()) {
            this.baseMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Product>()
                            .eq(Product::getId, product.getId())
                            .set(Product::getStock, totalStock));
            product.setStock(totalStock);
        }
        
        // 5) 同步更新到 ES
        syncToEsAfterCommit(product.getId());
    }

    @Override
    public void deleteProduct(Long id) {
        // id 是商品 id，用 assertProductOwned（不是 assertShopOwned）
        ownershipChecker.assertProductOwned(id);
        boolean ok = this.removeById(id);   // @TableLogic → 逻辑删除
        if (!ok) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        deleteFromEsAfterCommit(id);
    }

    @Override
    public PageResult<Product> pageProducts(ProductPageQuery query) {
        org.springframework.data.elasticsearch.core.query.Criteria criteria = new org.springframework.data.elasticsearch.core.query.Criteria();

        // 1. 权限与店铺过滤 (替代原 applyShopFilter)
        List<Long> shopIds = ownershipChecker.myShopIds();
        if (shopIds != null && !shopIds.isEmpty()) {
            if (query.getShopId() != null) {
                if (!shopIds.contains(query.getShopId())) {
                    return new PageResult<>(); // 越权
                }
                criteria.and(new org.springframework.data.elasticsearch.core.query.Criteria("shopId").is(query.getShopId()));
            } else {
                criteria.and(new org.springframework.data.elasticsearch.core.query.Criteria("shopId").in(shopIds));
            }
        } else if (shopIds != null) {
            return new PageResult<>(); // 商家无店铺
        } else if (query.getShopId() != null) {
            criteria.and(new org.springframework.data.elasticsearch.core.query.Criteria("shopId").is(query.getShopId()));
        }

        // 2. 多门店过滤
        if (query.getShopId() == null && StringUtils.hasText(query.getShopIds())) {
            java.util.List<Long> ids = new java.util.ArrayList<>();
            for (String s : query.getShopIds().split(",")) {
                try { ids.add(Long.parseLong(s.trim())); } catch (Exception ignored) {}
            }
            if (!ids.isEmpty()) {
                criteria.and(new org.springframework.data.elasticsearch.core.query.Criteria("shopId").in(ids));
            }
        }

        // 3. 常规条件过滤
        if (query.getCategoryId() != null) criteria.and(new org.springframework.data.elasticsearch.core.query.Criteria("categoryId").is(query.getCategoryId()));
        if (query.getType() != null) criteria.and(new org.springframework.data.elasticsearch.core.query.Criteria("type").is(query.getType()));
        if (query.getStatus() != null) criteria.and(new org.springframework.data.elasticsearch.core.query.Criteria("status").is(query.getStatus()));
        if (query.getMinPrice() != null) criteria.and(new org.springframework.data.elasticsearch.core.query.Criteria("price").greaterThanEqual(query.getMinPrice().doubleValue()));
        if (query.getMaxPrice() != null) criteria.and(new org.springframework.data.elasticsearch.core.query.Criteria("price").lessThanEqual(query.getMaxPrice().doubleValue()));

        // 4. IK 分词全文检索
        if (StringUtils.hasText(query.getName())) {
            criteria.and(new org.springframework.data.elasticsearch.core.query.Criteria("name").matches(query.getName())
                    .or(new org.springframework.data.elasticsearch.core.query.Criteria("description").matches(query.getName())));
        }

        org.springframework.data.elasticsearch.core.query.CriteriaQuery cq = new org.springframework.data.elasticsearch.core.query.CriteriaQuery(criteria);

        // 5. 排序处理
        String sort = query.getSort();
        if ("sales_desc".equals(sort)) {
            cq.addSort(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "sales"));
        } else if ("price_asc".equals(sort)) {
            cq.addSort(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "price"));
        } else if ("price_desc".equals(sort)) {
            cq.addSort(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "price"));
        } else if ("new".equals(sort)) {
            cq.addSort(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createTime"));
        } else if ("recommend".equals(sort)) {
            // 推荐排序：ES 层面先按销量兜底排序，等拿回数据后再进行内存重排
            cq.addSort(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "sales"));
        } else {
            cq.addSort(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createTime"));
        }

        // 6. 分页请求 (ES 的 page 是从 0 开始的)
        cq.setPageable(org.springframework.data.domain.PageRequest.of((int)(query.getCurrent() - 1), (int)query.getSize()));

        // 7. 执行 ES 查询
        org.springframework.data.elasticsearch.core.SearchHits<ProductES> hits = elasticsearchOperations.search(cq, ProductES.class);
        
        List<Product> resultList = new java.util.ArrayList<>();
        if (hits.getTotalHits() > 0) {
            // 提取出当前页的商品 ID 列表
            List<Long> productIds = hits.getSearchHits().stream()
                    .map(h -> h.getContent().getId())
                    .collect(java.util.stream.Collectors.toList());
            
            // 去 MySQL 查询出真实的商品数据（保持 ES 返回的排序）
            List<Product> dbProducts = this.listByIds(productIds);
            java.util.Map<Long, Product> productMap = dbProducts.stream().collect(java.util.stream.Collectors.toMap(Product::getId, p -> p));
            for (Long pid : productIds) {
                if (productMap.containsKey(pid)) {
                    resultList.add(productMap.get(pid));
                }
            }
            // 推荐排序：人工内存重排（千人千面）
            if ("recommend".equals(sort)) {
                List<Long> recIds = getRecommendProductIds(query.getSize() * 2);
                resultList.sort((a, b) -> {
                    int idxA = recIds.indexOf(a.getId());
                    int idxB = recIds.indexOf(b.getId());
                    if (idxA != -1 && idxB != -1) return Integer.compare(idxA, idxB);
                    if (idxA != -1) return -1;
                    if (idxB != -1) return 1;
                    return Integer.compare(b.getSales() != null ? b.getSales() : 0, a.getSales() != null ? a.getSales() : 0);
                });
            }
            
            applyDiscountAndShopName(resultList);
        }

        // 8. 构造 PageResult 统一返回格式
        PageResult<Product> pageResult = new PageResult<>();
        pageResult.setCurrent((long) query.getCurrent());
        pageResult.setSize((long) query.getSize());
        pageResult.setTotal(hits.getTotalHits());
        pageResult.setRecords(resultList);
        return pageResult;
    }

    // 移除旧的 applyShopFilter 和 applyMultiShopFilter (因为已被内联合并到上面)

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
            return recommendProducts(n, UserContext.getUserId());
        }
        if ("NEW".equalsIgnoreCase(strategy)) {
            return homeNewProducts(n);
        }
        if ("CF".equalsIgnoreCase(strategy)) {
            return homeCfProducts(n, UserContext.getUserId());
        }
        // HOT / 默认 → 按销量
        LambdaQueryWrapper<Product> w = new LambdaQueryWrapper<>();
        w.eq(Product::getStatus, 1).orderByDesc(Product::getSales);
        return getPageWithFallbackProtection(w, n, 1);
    }

    @Override
    public List<com.petshop.product.entity.HomeSectionVO> assembleHome(int limit) {
        int n = (limit <= 0) ? 6 : Math.min(limit, 50);
        // 在主线程获取 userId，防止在 CompletableFuture 异步线程中 ThreadLocal 丢失
        Long userId = UserContext.getUserId();

        // 1. 动态定义楼层配置（未来可放进数据库/配置中心，支持A/B测试）
        List<com.petshop.product.entity.HomeSectionVO> configs = java.util.Arrays.asList(
            new com.petshop.product.entity.HomeSectionVO("CF", "🛍️ 大家都在买", null),
            new com.petshop.product.entity.HomeSectionVO("RECOMMEND", "💡 为你推荐", null),
            new com.petshop.product.entity.HomeSectionVO("HOT", "🔥 热销榜单", null),
            new com.petshop.product.entity.HomeSectionVO("NEW", "✨ 新鲜上架", null)
        );

        // 2. 动态发起并发任务，使用专属线程池，添加熔断超时和异常降级
        List<java.util.concurrent.CompletableFuture<com.petshop.product.entity.HomeSectionVO>> futures = new java.util.ArrayList<>();
        
        for (com.petshop.product.entity.HomeSectionVO config : configs) {
            java.util.concurrent.CompletableFuture<com.petshop.product.entity.HomeSectionVO> future = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                List<Product> products = new java.util.ArrayList<>();
                switch (config.getTag()) {
                    case "CF":
                        products = homeCfProducts(n, userId);
                        break;
                    case "RECOMMEND":
                        products = recommendProducts(n, userId);
                        break;
                    case "NEW":
                        products = homeNewProducts(n);
                        break;
                    case "HOT":
                    default:
                        LambdaQueryWrapper<Product> w = new LambdaQueryWrapper<>();
                        w.eq(Product::getStatus, 1).orderByDesc(Product::getSales);
                        products = getPageWithFallbackProtection(w, n, 1);
                        break;
                }
                config.setList(products);
                return config;
            }, homeAssemblyThreadPool)
            // 工业级：如果某个算法卡死，500ms 后强制熔断返回空数据，防止整个首页雪崩
            .completeOnTimeout(new com.petshop.product.entity.HomeSectionVO(config.getTag(), config.getTitle(), new java.util.ArrayList<>()), 500, java.util.concurrent.TimeUnit.MILLISECONDS)
            // 工业级：局部异常隔离，某一路算法报错不影响其他楼层
            .exceptionally(e -> {
                log.error("首页装配楼层异常: " + config.getTag(), e);
                return new com.petshop.product.entity.HomeSectionVO(config.getTag(), config.getTitle(), new java.util.ArrayList<>());
            });
            futures.add(future);
        }

        // 3. 阻塞等待所有并发请求完成，或部分降级完成
        java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();

        // 4. 按原有配置顺序组装返回
        List<com.petshop.product.entity.HomeSectionVO> result = new java.util.ArrayList<>();
        for (java.util.concurrent.CompletableFuture<com.petshop.product.entity.HomeSectionVO> f : futures) {
            try {
                result.add(f.get());
            } catch (Exception e) {
                // 已被 exceptionally 兜底，理论上不会走到这里
            }
        }
        return result;
    }

    /** 首页「最新上架」策略 */
    private List<Product> homeNewProducts(int n) {
        LambdaQueryWrapper<Product> w = new LambdaQueryWrapper<>();
        w.eq(Product::getStatus, 1).orderByDesc(Product::getCreateTime);
        return getPageWithFallbackProtection(w, n, 1);
    }

    /** 首页「协同过滤推荐」策略：登录用户取 CF 推荐结果，未命中则错峰兜底 */
    private List<Product> homeCfProducts(int n, Long userId) {
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

    private List<Product> recommendProducts(int n, Long userId) {
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
        if (userId == null) {
            return java.util.Collections.emptyList();
        }

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
            try { 
                tagIds.add(Long.parseLong(s)); 
            } 
            catch (NumberFormatException ignored) { 
                /* 非数字标签跳过 */ 
            }
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
        } catch (Exception ignored) { 
            /* recommend_result 表可能尚未创建，忽略异常继续执行 */ 
        }
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
        // 同步扣减主表 stock（主表 stock = 所有 SKU 库存之和）
        this.baseMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Product>()
                        .eq(Product::getId, productId)
                        .setSql("stock = stock - " + quantity));
        autoDelistIfOutOfStock(productId);
        syncToEsAfterCommit(productId); // 同步库存和下架状态到 ES
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
        autoDelistIfOutOfStock(product.getId());
        syncToEsAfterCommit(product.getId()); // 同步库存和下架状态到 ES
        return product.getPrice();
    }

    /** 库存归零时自动下架商品（主表 stock 已保证与 SKU 总和同步） */
    private void autoDelistIfOutOfStock(Long productId) {
        this.baseMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Product>()
                        .eq(Product::getId, productId)
                        .eq(Product::getStatus, 1)
                        .le(Product::getStock, 0)
                        .set(Product::getStatus, 0));
    }

    @Override
    public List<Product> getAllActiveProductsForRecommend() {
        LambdaQueryWrapper<Product> w = new LambdaQueryWrapper<>();
        w.eq(Product::getStatus, 1);
        w.select(Product::getId, Product::getCategoryId, Product::getName, Product::getSales);
        return this.list(w);
    }
}
