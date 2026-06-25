package com.petshop.user.service;

import com.petshop.user.model.dto.OAuthLoginDTO;
import com.petshop.user.model.vo.OAuthLoginVO;

public interface UserOauthService {

    /** 第三方 OAuth 登录。已绑定返回 token+用户信息，未绑定返回 bindRequired=true */
    OAuthLoginVO login(OAuthLoginDTO dto);
}
