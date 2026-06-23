package com.petshop.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.petshop.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 退单 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("refund")
public class Refund extends BaseEntity {

    private String refundNo;
    private Long orderId;
    private Long userId;
    private BigDecimal amount;
    private String reason;
    /** 1用户申请 2管理员直接退 */
    private Integer type;
    /** 0申请中 1审核通过(已退) 2审核拒绝 */
    private Integer status;
    private Long auditUserId;
    private LocalDateTime auditTime;
    private String auditRemark;
}
