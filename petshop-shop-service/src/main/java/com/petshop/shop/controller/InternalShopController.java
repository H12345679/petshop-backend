package com.petshop.shop.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petshop.shop.entity.Shop;
import com.petshop.shop.mapper.ShopMapper;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 服务间内部接口（供其它微服务经 Feign 调用，见 common 的 ShopClient）。
 * <p>
 * /internal/** 不在网关路由表内，外部前端无法经网关访问。
 */
@Hidden
@RestController
@RequestMapping("/internal/shops")
public class InternalShopController {

    @Autowired
    private ShopMapper shopMapper;

    /** 查店主用户 ID；店铺不存在返回 null（Feign 侧收到空响应体即 null）。 */
    @GetMapping("/{shopId}/owner-id")
    public Long getShopOwnerId(@PathVariable("shopId") Long shopId) {
        Shop shop = shopMapper.selectById(shopId);
        return shop == null ? null : shop.getOwnerId();
    }

    /** 某用户名下所有店铺 ID。 */
    @GetMapping("/ids-by-owner")
    public List<Long> getShopIdsByOwner(@RequestParam("ownerId") Long ownerId) {
        return shopMapper.selectList(new LambdaQueryWrapper<Shop>().eq(Shop::getOwnerId, ownerId))
                .stream().map(Shop::getId).toList();
    }
}
