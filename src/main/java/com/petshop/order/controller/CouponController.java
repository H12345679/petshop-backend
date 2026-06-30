package com.petshop.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.petshop.common.Result;
import com.petshop.order.entity.Coupon;
import com.petshop.order.entity.UserCoupon;
import com.petshop.order.service.CouponService;
import com.petshop.security.RequireLogin;
import com.petshop.security.RequireRole;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 优惠券接口（对应《项目接口设计文档》C 模块第 6~9 节）。
 * <p>
 * 本 Controller 的 @RequestMapping 设为 "/api"，以便同时承载 /api/coupons 和
 * /api/users/me/coupons 两组路径（后者是 C 模块负责但挂在 /api/users 下的接口）。
 */
@Api(tags = "05-优惠券")
@RestController
@RequestMapping("/api")
public class CouponController {

    @Autowired
    private CouponService couponService;

    // ==================== 前台 ====================

    @ApiOperation("领券中心列表（公开）")
    @GetMapping("/coupons")
    public Result<List<Coupon>> listCenter() {
        return Result.success(couponService.listAvailable());
    }

    @ApiOperation("领用优惠券")
    @RequireLogin
    @PostMapping("/coupons/{id}/receive")
    public Result<Void> receive(@PathVariable Long id) {
        couponService.receive(id);
        return Result.success();
    }

    @ApiOperation("我的优惠券列表")
    @RequireLogin
    @GetMapping("/users/me/coupons")
    public Result<List<UserCoupon>> myCoupons(@RequestParam(required = false) Integer status) {
        return Result.success(couponService.listMyCoupons(status));
    }

    // ==================== 后台（ADMIN） ====================

    @ApiOperation("后台新增优惠券（仅 ADMIN）")
    @RequireRole("ADMIN")
    @PostMapping("/coupons")
    public Result<Coupon> create(@RequestBody Coupon coupon) {
        couponService.createCoupon(coupon);
        return Result.success(coupon);
    }

    @ApiOperation("后台编辑/上下架优惠券（仅 ADMIN）")
    @RequireRole("ADMIN")
    @PutMapping("/coupons/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Coupon coupon) {
        couponService.updateCoupon(id, coupon);
        return Result.success();
    }

    @ApiOperation("后台删除优惠券（仅 ADMIN）")
    @RequireRole("ADMIN")
    @DeleteMapping("/coupons/{id}")
    public Result<Void> deleteCoupon(@PathVariable Long id) {
        couponService.deleteCoupon(id);
        return Result.success();
    }

    @ApiOperation("后台优惠券分页列表（仅 ADMIN）")
    @RequireRole("ADMIN")
    @GetMapping("/coupons/manage")
    public Result<Map<String, Object>> managePage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer status) {
        Page<Coupon> page = couponService.managePage(current, size, name, type, status);
        Map<String, Object> result = new HashMap<>();
        result.put("total", page.getTotal());
        result.put("pages", page.getPages());
        result.put("current", page.getCurrent());
        result.put("size", page.getSize());
        result.put("records", page.getRecords());
        return Result.success(result);
    }
}
