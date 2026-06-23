package com.petshop.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.petshop.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 订单（表名 orders，order 是保留字） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("orders")
public class Order extends BaseEntity {

    private String orderNo;
    private Long userId;
    /** 商店id(一个订单只属于一个商店) */
    private Long shopId;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal payAmount;
    /** 使用的券id 0=未用 */
    private Long couponId;
    /** 0待支付 1待发货 2待收货 3待评价 4已完成 -1已取消 -2退款申请中 -3已退款 -4管理员退款 */
    private Integer status;
    /** 申请退款前的状态(用于退款被拒后恢复) */
    private Integer prevStatus;
    /** 1余额 2模拟支付 */
    private Integer payType;
    private LocalDateTime payTime;
    private LocalDateTime shipTime;
    private LocalDateTime receiveTime;
    private LocalDateTime finishTime;
    private String cancelReason;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private String remark;
}
