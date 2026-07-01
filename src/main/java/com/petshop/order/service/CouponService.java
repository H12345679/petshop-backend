package com.petshop.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.petshop.order.entity.Coupon;

import java.util.List;
import java.util.Map;

/**
 * 优惠券服务接口（对应《项目接口设计文档》C 模块第 6~9 节）。
 */
public interface CouponService {

    // ========== 前台 ==========

    /** 领券中心：获取所有有效且未过期的优惠券列表 */
    List<Coupon> listAvailable();

    /** 用户领券 */
    void receive(Long couponId);

    /** 当前登录用户已领的优惠券（含券定义明细：name/type/amount/threshold/endTime 等） */
    List<Map<String, Object>> listMyCoupons(Integer status);

    // ========== 后台 ==========

    /** 后台新增优惠券（ADMIN） */
    void createCoupon(Coupon coupon);

    /** 后台编辑/上下架（ADMIN） */
    void updateCoupon(Long id, Coupon coupon);

    /** 后台分页列表（ADMIN，支持券名/类型/状态筛选） */
    Page<Coupon> managePage(int current, int size, String name, Integer type, Integer status);

    /** 后台删除优惠券（ADMIN） */
    void deleteCoupon(Long id);
}
