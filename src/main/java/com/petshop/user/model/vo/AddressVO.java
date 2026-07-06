package com.petshop.user.model.vo;

import lombok.Data;

import com.petshop.common.annotation.Desensitize;
import com.petshop.common.annotation.DesensitizeType;

/** 收货地址响应 */
@Data
public class AddressVO {

    private Long id;
    private String receiver;
    @Desensitize(DesensitizeType.PHONE)
    private String phone;
    private String province;
    private String city;
    private String district;
    private String detail;
    private java.math.BigDecimal longitude;
    private java.math.BigDecimal latitude;
    /** 1默认 0非默认 */
    private Integer isDefault;
}
