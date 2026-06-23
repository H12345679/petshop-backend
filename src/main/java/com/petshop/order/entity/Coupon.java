package com.petshop.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.petshop.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 优惠券 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("coupon")
public class Coupon extends BaseEntity {

    private String name;
    /** 1满减 2折扣 */
    private Integer type;
    /** 满X元可用 */
    private BigDecimal threshold;
    /** 减Y元 或 折扣(0.9=9折) */
    private BigDecimal amount;
    private Integer total;
    private Integer remain;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    /** 1有效 0停用 */
    private Integer status;
}
