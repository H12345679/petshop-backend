package com.petshop.api.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 店铺服务 Feign 客户端（服务间内部调用）。
 * <p>
 * 供 {@link com.petshop.security.OwnershipChecker} 在非 shop 服务内做「商家归属校验」：
 * 通过 Nacos 服务发现定位 petshop-shop-service 实例并负载均衡调用。
 * 对应实现见 shop 服务的 InternalShopController。
 */
@FeignClient(name = "petshop-shop-service", contextId = "shopClient")
public interface ShopClient {

    /** 查店主用户 ID；店铺不存在时返回 null（响应体为空）。 */
    @GetMapping("/internal/shops/{shopId}/owner-id")
    Long getShopOwnerId(@PathVariable("shopId") Long shopId);

    /** 某用户名下所有店铺 ID（无店铺返回空列表）。 */
    @GetMapping("/internal/shops/ids-by-owner")
    List<Long> getShopIdsByOwner(@RequestParam("ownerId") Long ownerId);
}
