package com.petshop.shop.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.petshop.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 商品规格 SKU（有规格商品以本表 price/stock 为准） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_sku")
public class ProductSku extends BaseEntity {

    private Long productId;
    /** 规格描述 如"颜色:红;尺寸:L" */
    private String specName;
    private BigDecimal price;
    private Integer stock;
    private String image;
}
