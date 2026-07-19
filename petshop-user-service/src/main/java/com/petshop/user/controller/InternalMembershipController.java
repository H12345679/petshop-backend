package com.petshop.user.controller;

import com.petshop.user.service.MembershipLevelService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 服务间内部接口（供商品/订单服务经 Feign 调用，见 common 的 MembershipClient）。
 * <p>
 * /internal/** 不在网关路由表内，外部前端无法经网关访问。
 */
@Hidden
@RestController
@RequestMapping("/internal/membership")
public class InternalMembershipController {

    @Autowired
    private MembershipLevelService membershipLevelService;

    /** 按用户 ID 查会员折扣率；userId 缺省（未登录场景）返回 1.0。 */
    @GetMapping("/discount")
    public BigDecimal getUserDiscount(@RequestParam(value = "userId", required = false) Long userId) {
        return membershipLevelService.discountForUser(userId);
    }

    /** 按用户 ID 查会员等级名称；非会员返回 null。 */
    @GetMapping("/level-name")
    public String getUserLevelName(@RequestParam(value = "userId", required = false) Long userId) {
        return membershipLevelService.levelNameForUser(userId);
    }
}
