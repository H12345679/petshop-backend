package com.petshop.shop.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.petshop.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

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

    /** 子分类（非数据库字段，查询后在代码里组装成树）。@TableField(exist=false) 告诉 MP 别去查这列 */
    @TableField(exist = false)
    private List<ProductCategory> children;
}
