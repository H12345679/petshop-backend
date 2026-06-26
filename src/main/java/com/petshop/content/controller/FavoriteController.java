package com.petshop.content.controller;

import com.petshop.common.PageResult;
import com.petshop.common.Result;
import com.petshop.content.service.FavoriteService;
import com.petshop.security.RequireLogin;
import com.petshop.product.entity.Product;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import com.petshop.common.annotation.TrackBehavior;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * E 模块 - 收藏接口（E3）
 */
@Api(tags = "07-E模块-商品收藏")
@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    @Autowired
    private FavoriteService favoriteService;

    @ApiOperation("添加商品收藏")
    @RequireLogin
    @TrackBehavior(type = 2, productIdSpEL = "#productId")
    @PostMapping("/{productId}")
    public Result<Void> addFavorite(@ApiParam("商品ID") @PathVariable Long productId) {
        favoriteService.addFavorite(productId);
        return Result.success();
    }

    @ApiOperation("取消商品收藏")
    @RequireLogin
    @DeleteMapping("/{productId}")
    public Result<Void> removeFavorite(@ApiParam("商品ID") @PathVariable Long productId) {
        favoriteService.removeFavorite(productId);
        return Result.success();
    }

    @ApiOperation("检查是否已收藏该商品")
    @RequireLogin
    @GetMapping("/{productId}/check")
    public Result<Map<String, Integer>> checkFavorite(@ApiParam("商品ID") @PathVariable Long productId) {
        int isFavorite = favoriteService.checkFavorite(productId);
        Map<String, Integer> map = new HashMap<>();
        map.put("isFavorite", isFavorite);
        return Result.success(map);
    }

    @ApiOperation("分页获取我的收藏列表")
    @RequireLogin
    @GetMapping
    public Result<PageResult<Product>> getFavoriteList(
            @ApiParam("当前页") @RequestParam(defaultValue = "1") long current,
            @ApiParam("每页大小") @RequestParam(defaultValue = "10") long size) {
        PageResult<Product> pageResult = favoriteService.getFavoriteList(current, size);
        return Result.success(pageResult);
    }
}
