package com.petshop.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
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

    /** 生效时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /** 失效时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /** 1有效 0停用 */
    private Integer status;
}
