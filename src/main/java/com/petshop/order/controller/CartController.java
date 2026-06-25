package com.petshop.order.controller;

import com.petshop.common.Result;
import com.petshop.order.entity.CartItem;
import com.petshop.order.service.CartService;
import com.petshop.security.RequireLogin;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 购物车接口（对应《项目接口设计文档》C 模块第 1~5 节）。
 */
@Api(tags = "04-购物车")
@RestController
@RequestMapping("/api/cart")
@RequireLogin
public class CartController {

    @Autowired
    private CartService cartService;

    @ApiOperation("加入购物车（同款存在则递增数量）")
    @PostMapping
    public Result<Void> add(@RequestBody CartItem cartItem) {
        cartService.addToCart(cartItem);
        return Result.success();
    }

    @ApiOperation("购物车列表（含商品名/规格/实时价格/有效性校验）")
    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        return Result.success(cartService.getCartList());
    }

    @ApiOperation("更新购物车数量")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody CartItem body) {
        cartService.updateQuantity(id, body.getQuantity());
        return Result.success();
    }

    @ApiOperation("删除购物车项")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        cartService.removeFromCart(id);
        return Result.success();
    }

    @ApiOperation("切换勾选状态")
    @PutMapping("/{id}/select")
    public Result<Void> toggleSelect(@PathVariable Long id, @RequestBody CartItem body) {
        cartService.toggleSelect(id, body.getSelected());
        return Result.success();
    }
}
