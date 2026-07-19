package com.petshop.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.petshop.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 用户优惠券 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_coupon")
public class UserCoupon extends BaseEntity {

    private Long userId;
    private Long couponId;
    /** 0未使用 1已使用 2已过期 */
    private Integer status;
    private LocalDateTime usedTime;
    /** 使用的订单id */
    private Long orderId;
}
