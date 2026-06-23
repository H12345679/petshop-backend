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
    private String spec;
    private BigDecimal price;
    private Integer quantity;
    /** 小计 = price * quantity */
    private BigDecimal subtotal;
    /** 分摊优惠后实付金额，退款时以此为上限 */
    private BigDecimal realPayAmount;
}
