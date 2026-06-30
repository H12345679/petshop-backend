package com.petshop.order.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.petshop.common.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime endTime;
    /** 1有效 0停用 */
    private Integer status;
    /** 店铺ID（null=全局券，非空=店铺券） */
    private Long shopId;
    /** 店铺名称（非数据库字段，仅后台列表展示用） */
    @TableField(exist = false)
    private String shopName;
}
