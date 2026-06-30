package com.petshop.user.service;

import com.petshop.user.model.dto.LoginDTO;
import com.petshop.user.model.dto.RegisterDTO;
import com.petshop.user.model.dto.UpdateUserDTO;
import com.petshop.user.model.vo.LoginVO;
import com.petshop.user.model.vo.UserVO;

import java.math.BigDecimal;

public interface UserService {

    /** 用户注册 */
    UserVO register(RegisterDTO dto);

    /** 用户登录 */
    LoginVO login(LoginDTO dto);

    /** 获取当前登录用户信息 */
    UserVO getCurrentUser(Long userId);

    /** 修改当前用户信息 */
    void updateCurrentUser(Long userId, UpdateUserDTO dto);

    /** 账户充值，返回新余额 */
    BigDecimal recharge(Long userId, BigDecimal amount);
}
