package com.petshop.content.controller;

import com.petshop.common.PageResult;
import com.petshop.common.Result;
import com.petshop.content.service.FavoriteService;
import com.petshop.security.RequireLogin;
import com.petshop.product.entity.Product;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import com.petshop.common.annotation.TrackBehavior;
import com.petshop.log.annotation.LogOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * E 模块 - 收藏接口（E3）
 */
@Tag(name = "07-E模块-商品收藏")
@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    @Autowired
    private FavoriteService favoriteService;

    @Operation(summary = "添加商品收藏")
    @RequireLogin
    @LogOperation("用户收藏商品(埋点)")
    @TrackBehavior(type = 2, productIdSpEL = "#productId")
    @PostMapping("/{productId}")
    public Result<Void> addFavorite(@Parameter(description = "商品ID") @PathVariable Long productId) {
        favoriteService.addFavorite(productId);
        return Result.success();
    }

    @Operation(summary = "取消商品收藏")
    @RequireLogin
    @DeleteMapping("/{productId}")
    public Result<Void> removeFavorite(@Parameter(description = "商品ID") @PathVariable Long productId) {
        favoriteService.removeFavorite(productId);
        return Result.success();
    }

    @Operation(summary = "检查是否已收藏该商品")
    @RequireLogin
    @GetMapping("/{productId}/check")
    public Result<Map<String, Integer>> checkFavorite(@Parameter(description = "商品ID") @PathVariable Long productId) {
        int isFavorite = favoriteService.checkFavorite(productId);
        Map<String, Integer> map = new HashMap<>();
        map.put("isFavorite", isFavorite);
        return Result.success(map);
    }

    @Operation(summary = "分页获取我的收藏列表")
    @RequireLogin
    @GetMapping
    public Result<PageResult<Product>> getFavoriteList(
            @Parameter(description = "当前页") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") long size) {
        PageResult<Product> pageResult = favoriteService.getFavoriteList(current, size);
        return Result.success(pageResult);
    }
}
