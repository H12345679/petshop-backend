package com.petshop.shop.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.petshop.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_customer")
public class ShopCustomer extends BaseEntity {
    private Long shopId;
    private Long userId;
    private LocalDateTime lastPurchaseTime;
}
