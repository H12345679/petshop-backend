package com.petshop.product.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.petshop.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;

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

    /** 多规格列表（非数据库字段：创建时接收前端传入、详情时组装返回） */
    @TableField(exist = false)
    private List<ProductSku> skus;
}
