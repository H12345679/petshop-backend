package com.petshop.user.model.vo;

import lombok.Data;

/** 第三方 OAuth 登录响应 */
@Data
public class OAuthLoginVO {

    /** 是否需要绑定已有账号 */
    private Boolean bindRequired;
    /** 已绑定时返回 token */
    private String token;
    /** 已绑定时返回用户信息 */
    private UserVO user;
    /** 未绑定时返回，供前端绑定使用 */
    private String provider;
    /** 未绑定时返回，供前端绑定使用 */
    private String openId;
}
