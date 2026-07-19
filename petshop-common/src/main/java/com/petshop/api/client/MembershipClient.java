package com.petshop.api.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

/**
 * 会员体系 Feign 客户端（服务间内部调用）。
 * <p>
 * 商品服务（会员价展示）与订单服务（结算折扣）跨服务查询会员折扣。
 * userId 为 null（未登录浏览）时 Feign 会省略该查询参数，服务端按未登录处理返回 1.0。
 * 对应实现见 user 服务的 InternalMembershipController。
 */
@FeignClient(name = "petshop-user-service", contextId = "membershipClient")
public interface MembershipClient {

    /** 按用户 ID 查会员折扣率（无折扣/未登录返回 1.0）。 */
    @GetMapping("/internal/membership/discount")
    BigDecimal getUserDiscount(@RequestParam(value = "userId", required = false) Long userId);

    /** 按用户 ID 查会员等级名称（非会员返回 null）。 */
    @GetMapping("/internal/membership/level-name")
    String getUserLevelName(@RequestParam(value = "userId", required = false) Long userId);
}
