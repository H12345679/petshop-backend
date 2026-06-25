package com.petshop.product.controller;

import com.petshop.common.PageResult;
import com.petshop.common.Result;
import com.petshop.product.entity.Product;
import com.petshop.product.service.ProductPageQuery;
import com.petshop.product.service.ProductService;
import com.petshop.security.RequireRole;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import com.petshop.common.annotation.TrackBehavior;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 商品接口（对应《项目接口设计文档》A 模块第 7~11 节）。
 */
@Api(tags = "03-商品")
@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    @ApiOperation("创建商品（含多 SKU，ADMIN/MERCHANT）")
    @RequireRole({"ADMIN", "MERCHANT"})
    @PostMapping
    public Result<Product> createProduct(@Valid @RequestBody Product product) {
        productService.createProduct(product);
        // product 此时已带回主键 id，skus 也已绑定 productId，直接返回作为「创建成功的商品详情」
        return Result.success(product);
    }

    @ApiOperation("商品详情查询（公开，核心接口）")
    @TrackBehavior(type = 1, productIdSpEL = "#id")
    @GetMapping("/{id}")
    public Result<Product> getProduct(@PathVariable Long id) {
        return Result.success(productService.getProductById(id));
    }

    @ApiOperation("商品修改（ADMIN/MERCHANT 本店）")
    @RequireRole({"ADMIN", "MERCHANT"})
    @PutMapping("/{id}")
    public Result<Product> updateProduct(@PathVariable Long id, @Valid @RequestBody Product product) {
        product.setId(id);   // 用路径上的 id 作为修改目标，避免改错对象
        productService.updateProduct(product);
        return Result.success(product);
    }

    @ApiOperation("商品删除（逻辑删除，ADMIN/MERCHANT 本店）")
    @RequireRole({"ADMIN", "MERCHANT"})
    @DeleteMapping("/{id}")
    public Result<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return Result.success();
    }

    @ApiOperation("商品列表分页搜索（公开，前台/后台通用）")
    @GetMapping
    public Result<PageResult<Product>> page(ProductPageQuery query) {
        return Result.success(productService.pageProducts(query));
    }
}
