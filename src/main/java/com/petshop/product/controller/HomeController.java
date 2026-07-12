package com.petshop.product.controller;

import com.petshop.common.Result;
import com.petshop.product.entity.Product;
import com.petshop.product.service.ProductService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 首页接口（对应《项目接口设计文档》A 模块第 12 节）。
 * 公开接口，无需登录。第一阶段只做 HOT/NEW；RECOMMEND 属第二阶段加分项，先回退 HOT。
 */
@Tag(name = "04-首页")
@RestController
@RequestMapping("/api/home")
public class HomeController {

    @Autowired
    private ProductService productService;

    @Operation(summary = "首页商品展示（公开；strategy=HOT 热销 / NEW 上新；RECOMMEND 第一阶段回退 HOT）")
    @GetMapping("/products")
    public Result<List<Product>> products(
            @RequestParam(defaultValue = "HOT") String strategy,
            @RequestParam(defaultValue = "6") Integer limit) {
        return Result.success(productService.homeProducts(strategy, limit));
    }

    @Operation(summary = "企业级首页展示规则引擎：动态聚合楼层数据（含熔断降级隔离）")
    @GetMapping("/index")
    public Result<List<com.petshop.product.entity.HomeSectionVO>> homeIndex(
            @RequestParam(defaultValue = "6") Integer limit) {
        return Result.success(productService.assembleHome(limit));
    }
}
