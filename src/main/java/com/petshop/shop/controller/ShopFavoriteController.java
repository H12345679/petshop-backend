package com.petshop.shop.controller;

import com.petshop.common.PageQuery;
import com.petshop.common.PageResult;
import com.petshop.common.Result;
import com.petshop.shop.entity.Shop;
import com.petshop.shop.service.ShopFavoriteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/shop-favorites")
public class ShopFavoriteController {

    @Autowired
    private ShopFavoriteService shopFavoriteService;

    @GetMapping
    public Result<PageResult<Shop>> pageMyFavorites(PageQuery query) {
        return Result.success(shopFavoriteService.pageMyFavorites(query));
    }

    @PostMapping("/{shopId}")
    public Result<Void> addFavorite(@PathVariable Long shopId) {
        shopFavoriteService.addFavorite(shopId);
        return Result.success();
    }

    @DeleteMapping("/{shopId}")
    public Result<Void> removeFavorite(@PathVariable Long shopId) {
        shopFavoriteService.removeFavorite(shopId);
        return Result.success();
    }

    @GetMapping("/{shopId}/check")
    public Result<Boolean> checkFavorite(@PathVariable Long shopId) {
        return Result.success(shopFavoriteService.checkFavorite(shopId));
    }
}
