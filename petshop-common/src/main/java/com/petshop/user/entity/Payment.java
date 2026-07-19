package com.petshop.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.petshop.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 支付流水 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payment")
public class Payment extends BaseEntity {

    private String paymentNo;
    private Long orderId;
    private Long userId;
    private BigDecimal amount;
    /** 1余额 2模拟支付 */
    private Integer payType;
    /** 0待支付 1成功 2失败 */
    private Integer status;
    private LocalDateTime payTime;
}
