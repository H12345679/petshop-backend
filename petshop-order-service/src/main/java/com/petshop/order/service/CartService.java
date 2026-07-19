package com.petshop.order.service;

import com.petshop.order.entity.CartItem;

import java.util.List;
import java.util.Map;

/**
 * 购物车服务接口（对应《项目接口设计文档》C 模块第 1~5 节）。
 */
public interface CartService {

    /**
     * 加入购物车。若该用户对该规格已有记录，则递增 quantity；否则新增。
     */
    void addToCart(CartItem cartItem);

    /**
     * 更新购物车项数量。只能操作本人的购物车。
     */
    void updateQuantity(Long cartId, Integer quantity);

    /**
     * 删除购物车项（物理删除）。只能操作本人的购物车。
     */
    void removeFromCart(Long cartId);

    /**
     * 切换勾选状态。
     */
    void toggleSelect(Long cartId, Integer selected);

    /**
     * 获取当前登录用户的购物车列表，含商品名/图/规格/实时价格/有效性校验。
     */
    List<Map<String, Object>> getCartList();
}
