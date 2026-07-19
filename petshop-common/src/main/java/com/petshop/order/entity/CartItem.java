package com.petshop.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.petshop.common.BaseEntityLite;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 购物车项。注意：继承 BaseEntityLite（无 deleted），走物理删除，配合唯一键防重复加车。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cart_item")
public class CartItem extends BaseEntityLite {

    private Long userId;
    private Long productId;
    /** 规格id 0=无规格 */
    private Long skuId;
    private Integer quantity;
    /** 是否勾选结算 1是 0否 */
    private Integer selected;
}
