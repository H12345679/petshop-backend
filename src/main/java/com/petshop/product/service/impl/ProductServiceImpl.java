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
import com.petshop.product.service.ProductPageQuery;
import com.petshop.product.service.ProductService;
import com.petshop.security.OwnershipChecker;
import com.petshop.security.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
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
        return product;
    }

    @Override
    public void updateProduct(Product product) {
        // 按「商品真实归属」校验：防止商家传自己的 shopId 却改别人的商品（越权）
        ownershipChecker.assertProductOwned(product.getId());
        product.setShopId(null);   // 不允许通过修改接口把商品挪到别的店
        boolean ok = this.updateById(product);
        if (!ok) {
            throw new BusinessException(ResultCode.NOT_FOUND);
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

        return PageResult.of(this.page(query.toPage(), w));
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
        if ("NEW".equalsIgnoreCase(strategy)) {
            w.orderByDesc(Product::getCreateTime);   // 新鲜上架
        } else {
            // HOT / RECOMMEND（第二阶段才做，先回退）/ 默认 → 按销量
            w.orderByDesc(Product::getSales);
        }
        // 取前 N 条：复用分页，要第 1 页、每页 n 条，拿 records（不写裸 LIMIT SQL）
        return this.page(new Page<>(1, n), w).getRecords();
    }

    private List<Product> recommendProducts(int n) {
        // 取当前登录用户 id；为空（未登录）→ 没有个性化数据，直接 return homeProducts("HOT", n);
        if(UserContext.getUserId()==null){
            return homeProducts("hot",n);
        }
        // 查推荐结果：RecommendResultMapper 按 user_id 查、score 降序，
        // 取出 product_id 列表（建议多取些，如 n*2，给「下架过滤 + 去重」留余量）。


        // 用这批 product_id 批量查 product（只要 status=1 上架的）；
        // 注意：IN 查询返回的顺序 ≠ 推荐顺序，需要自己按 product_id 列表重新排序。

        // 不足 n 条（冷启动/新用户）→ 用热销 homeProducts("HOT", n) 去重补足到 n。

        // （可选·营销加权）把「正在促销 / 有可领券」的商品适当置顶——依赖营销策略输出。

        // 未实现前，先整体回退热销：保证接口可用、且符合第一阶段约定 ——
        return homeProducts("HOT", n);
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
