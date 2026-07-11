package com.petshop.user.model.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/** 收货地址请求 */
@Data
public class AddressDTO {

    @NotBlank(message = "收货人不能为空")
    private String receiver;

    @NotBlank(message = "手机号不能为空")
    private String phone;

    @NotBlank(message = "省份不能为空")
    private String province;

    @NotBlank(message = "城市不能为空")
    private String city;

    @NotBlank(message = "区/县不能为空")
    private String district;

    @NotBlank(message = "详细地址不能为空")
    private String detail;

    private java.math.BigDecimal longitude;
    private java.math.BigDecimal latitude;

    /** 1默认 0非默认 */
    private Integer isDefault;
}
