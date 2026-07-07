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
    /** 关联订单明细ID，NULL=整单退款 */
    private Long orderItemId;
    private Long userId;
    private BigDecimal amount;
    private String reason;
    /** 问题描述（选填） */
    private String description;
    /** 凭证图片，JSON 数组字符串 */
    private String images;
    /** 1用户申请 2管理员直接退 */
    private Integer type;
    /** 1仅退款 2退货退款 */
    private Integer refundType;
    /** 申请时是否已收到货 0未收到(快递退款) 1已收到 */
    private Integer received;
    /** 0申请中 1已退款(结束) 2已驳回 3待用户退货 4待商家确认收货 */
    private Integer status;
    private Long auditUserId;
    private LocalDateTime auditTime;
    private String auditRemark;
    /** 退货物流公司（用户寄回时填写） */
    private String returnCourierCompany;
    /** 退货物流单号（用户寄回时填写） */
    private String returnTrackingNumber;
    /** 用户寄回时间 */
    private LocalDateTime returnTime;
}
