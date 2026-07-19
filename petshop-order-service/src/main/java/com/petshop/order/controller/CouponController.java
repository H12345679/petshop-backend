package com.petshop.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.petshop.common.Result;
import com.petshop.order.entity.Coupon;
import com.petshop.order.service.CouponService;
import com.petshop.security.RequireLogin;
import com.petshop.security.RequireRole;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.petshop.common.PageResult;
import java.util.List;
import java.util.Map;

/**
 * 优惠券接口
 */
@Tag(name = "05-优惠券")
@RestController
@RequestMapping("/api")
public class CouponController {

    @Autowired
    private CouponService couponService;

    // ==================== 前台 ====================

    @Operation(summary = "领券中心列表（公开）")
    @GetMapping("/coupons")
    public Result<List<Coupon>> listCenter() {
        return Result.success(couponService.listAvailable());
    }

    @Operation(summary = "领用优惠券")
    @RequireLogin
    @PostMapping("/coupons/{id}/receive")
    public Result<Void> receive(@PathVariable Long id) {
        couponService.receive(id);
        return Result.success();
    }

    @Operation(summary = "我的优惠券列表（含券定义明细）")
    @RequireLogin
    @GetMapping("/users/me/coupons")
    public Result<List<Map<String, Object>>> myCoupons(@RequestParam(required = false) Integer status) {
        return Result.success(couponService.listMyCoupons(status));
    }

    // ==================== 后台（ADMIN） ====================

    @Operation(summary = "后台新增优惠券（仅 ADMIN)")
    @RequireRole("ADMIN")
    @PostMapping("/coupons")
    public Result<Coupon> create(@RequestBody Coupon coupon) {
        couponService.createCoupon(coupon);
        return Result.success(coupon);
    }

    @Operation(summary = "后台编辑/上下架优惠券（仅 ADMIN)")
    @RequireRole("ADMIN")
    @PutMapping("/coupons/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Coupon coupon) {
        couponService.updateCoupon(id, coupon);
        return Result.success();
    }

    @Operation(summary = "后台删除优惠券（仅 ADMIN)")
    @RequireRole("ADMIN")
    @DeleteMapping("/coupons/{id}")
    public Result<Void> deleteCoupon(@PathVariable Long id) {
        couponService.deleteCoupon(id);
        return Result.success();
    }

    @Operation(summary = "后台优惠券分页列表（仅 ADMIN)")
    @RequireRole("ADMIN")
    @GetMapping("/coupons/manage")
    public Result<PageResult<Coupon>> managePage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer status) {
        Page<Coupon> page = couponService.managePage(current, size, name, type, status);
        return Result.success(PageResult.of(page));
    }
}
