package com.petshop.user.model.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/** 第三方 OAuth 登录请求 */
@Data
public class OAuthLoginDTO {

    @NotBlank(message = "平台标识不能为空")
    private String provider;

    @NotBlank(message = "openId不能为空")
    private String openId;

    private String unionId;
}
