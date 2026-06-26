package com.petshop.map.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.petshop.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 地图模块 - 商店实体（映射 shop 表）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop")
public class MapShop extends BaseEntity {

    /** 商店名称 */
    private String name;

    /** 商店简介 */
    private String description;

    /** 联系电话 */
    private String phone;

    /** 省 */
    private String province;

    /** 市 */
    private String city;

    /** 区/县 */
    private String district;

    /** 详细地址 */
    private String address;

    /** 经度 */
    private BigDecimal longitude;

    /** 纬度 */
    private BigDecimal latitude;

    /** 商店logo */
    private String logo;

    /** 店主用户id */
    private Long ownerId;

    /** 1营业 0停业 */
    private Integer status;
}
