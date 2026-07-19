package com.petshop.product.controller;

import com.petshop.common.Result;
import com.petshop.product.entity.ProductCategory;
import com.petshop.product.service.CategoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品分类接口。
 * 分类查询对所有人开放（前端导航、商品发布选分类都要用），故不加鉴权注解。
 */
@Tag(name = "02-商品分类")
@RestController
@RequestMapping("/api/categories")
public class ProductCategoriesController {

    @Autowired
    private CategoryService categoryService;

    @Operation(summary = "分类树查询（公开）")
    @GetMapping
    public Result<List<ProductCategory>> tree() {
        return Result.success(categoryService.tree());
    }
}
