package com.petshop.shop.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.petshop.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 商品分类 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_category")
public class ProductCategory extends BaseEntity {

    /** 父分类id 0=顶级 */
    private Long parentId;
    private String name;
    private Integer sort;
    private String icon;
}
