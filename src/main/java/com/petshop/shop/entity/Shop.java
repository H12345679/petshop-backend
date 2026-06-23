package com.petshop.shop.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.petshop.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 商店 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop")
public class Shop extends BaseEntity {

    private String name;
    private String description;
    private String phone;
    private String province;
    private String city;
    private String district;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String logo;
    private Long ownerId;
    /** 1营业 0停业 */
    private Integer status;
}
