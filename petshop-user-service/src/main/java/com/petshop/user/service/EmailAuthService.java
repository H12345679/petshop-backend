package com.petshop.user.service;

import com.petshop.user.model.dto.EmailLoginDTO;
import com.petshop.user.model.vo.LoginVO;

/** 邮箱验证码认证服务 */
public interface EmailAuthService {

    /** 发送验证码到指定邮箱 */
    void sendCode(String email);

    /** 验证码登录：校验通过后自动注册或返回已有账号的 JWT */
    LoginVO login(EmailLoginDTO dto);
}
