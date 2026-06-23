package com.petshop.shop.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.petshop.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 商品 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product")
public class Product extends BaseEntity {

    private Long shopId;
    private Long categoryId;
    private String name;
    /** 1宠物(唯一,库存=1) 2周边(数量不限) */
    private Integer type;
    private String description;
    /** 售价(无规格时用) */
    private BigDecimal price;
    private BigDecimal originalPrice;
    /** 库存(无规格时用) */
    private Integer stock;
    private Integer sales;
    private String mainImage;
    /** 多图 JSON 数组 */
    private String images;
    /** 1上架 0下架 */
    private Integer status;
}
