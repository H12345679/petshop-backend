package com.petshop.user.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.petshop.common.annotation.Desensitize;
import com.petshop.common.annotation.DesensitizeType;

/** 用户信息响应 */
@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    @Desensitize(DesensitizeType.PHONE)
    private String phone;
    @Desensitize(DesensitizeType.EMAIL)
    private String email;
    /** 0未知 1男 2女 */
    private Integer gender;
    /** USER / ADMIN / MERCHANT */
    private String role;
    /** 会员等级ID (0=非会员) */
    private Long memberLevelId;
    /** 账户余额 */
    private BigDecimal balance;
    /** 积分 */
    private Integer points;
    /** 1正常 0禁用 */
    private Integer status;
    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;
}
