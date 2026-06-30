package com.petshop.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.petshop.common.PageResult;
import com.petshop.product.entity.Product;

import java.util.List;

/**
 * 商品 Service 接口。单表 CRUD 由 IService 白送，这里只声明带业务逻辑的方法。
 */
public interface ProductService extends IService<Product> {

    /** 创建商品（含多 SKU）：事务内同时写 product 主表 + product_sku 子表。 */
    void createProduct(Product product);

    Product getProductById(Long id);

    void updateProduct(Product product);

    void deleteProduct(Long id);

    PageResult<Product> pageProducts(ProductPageQuery query);

    /** 首页商品：strategy=HOT 按销量 / NEW 按上架时间；其它（含 RECOMMEND）回退 HOT。取前 limit 条上架商品。 */
    List<Product> homeProducts(String strategy, Integer limit);

    /** [内部接口] 供订单模块使用：扣减库存并返回真实售价（带悲观/乐观锁思想，防超卖和篡改） */
    java.math.BigDecimal checkPriceAndDeductStock(Long productId, Long skuId, Integer quantity);

    /** [内部接口] 供推荐/统计模块使用：拉取所有上架状态的商品（轻量级） */
    List<Product> getAllActiveProductsForRecommend();
}
