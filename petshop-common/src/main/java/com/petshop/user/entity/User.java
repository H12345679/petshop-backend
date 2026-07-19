package com.petshop.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.petshop.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 用户 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user")
public class User extends BaseEntity {

    private String username;
    private String password;
    private String nickname;
    private String avatar;
    private String phone;
    private String email;
    /** 0未知 1男 2女 */
    private Integer gender;
    /** USER/ADMIN/MERCHANT */
    private String role;
    /** 会员等级id 0=游客/非会员 */
    private Long memberLevelId;
    /** 账户余额(余额支付用) */
    private BigDecimal balance;
    private Integer points;
    /** 1正常 0禁用 */
    private Integer status;
    private LocalDateTime lastLoginTime;
}
