package com.petshop.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.petshop.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 订单明细（下单时存商品快照） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_item")
public class OrderItem extends BaseEntity {

    private Long orderId;
    private Long productId;
    private Long skuId;
    private Long shopId;
    private String productName;
    private String productImage;
    private String specName;
    private BigDecimal price;
    private Integer quantity;
    /** 小计 = price * quantity */
    private BigDecimal subtotal;
    /** 分摊优惠后实付金额，退款时以此为上限 */
    private BigDecimal realPayAmount;
    /** 退款状态: 0正常 1退款中 2已退款 */
    private Integer refundStatus;
    /** 取消状态: 0正常 1已取消 */
    private Integer cancelStatus;
}
