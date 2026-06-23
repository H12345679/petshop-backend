package com.petshop.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.petshop.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 订单状态流转记录（状态机审计） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_status_log")
public class OrderStatusLog extends BaseEntity {

    private Long orderId;
    private Integer fromStatus;
    private Integer toStatus;
    private Long operatorId;
    /** 操作人角色 USER/ADMIN */
    private String operatorRole;
    private String remark;
}
