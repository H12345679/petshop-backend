package com.petshop.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("product_tag")
public class ProductTag {
    private Long id;
    private Long productId;
    private Long tagId;
}
