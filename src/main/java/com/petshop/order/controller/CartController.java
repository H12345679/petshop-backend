package com.petshop.order.controller;

import com.petshop.common.Result;
import com.petshop.order.entity.CartItem;
import com.petshop.order.service.CartService;
import com.petshop.security.RequireLogin;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.petshop.common.annotation.TrackBehavior;
import com.petshop.log.annotation.LogOperation;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 购物车接口（对应《项目接口设计文档》C 模块第 1~5 节）。
 */
@Tag(name = "04-购物车")
@RestController
@RequestMapping("/api/cart")
@RequireLogin
public class CartController {

    @Autowired
    private CartService cartService;

    @Operation(summary = "加入购物车（同款存在则递增数量）")
    @LogOperation("用户加入购物车(埋点)")
    @TrackBehavior(type = 3, productIdSpEL = "#cartItem.productId")
    @PostMapping
    public Result<Void> add(@RequestBody CartItem cartItem) {
        cartService.addToCart(cartItem);
        return Result.success();
    }

    @Operation(summary = "购物车列表（含商品名/规格/实时价格/有效性校验）")
    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        return Result.success(cartService.getCartList());
    }

    @Operation(summary = "更新购物车数量")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody CartItem body) {
        cartService.updateQuantity(id, body.getQuantity());
        return Result.success();
    }

    @Operation(summary = "删除购物车项")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        cartService.removeFromCart(id);
        return Result.success();
    }

    @Operation(summary = "切换勾选状态")
    @PutMapping("/{id}/select")
    public Result<Void> toggleSelect(@PathVariable Long id, @RequestBody CartItem body) {
        cartService.toggleSelect(id, body.getSelected());
        return Result.success();
    }
}
