package com.petshop.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.petshop.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 第三方登录绑定 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_oauth")
public class UserOauth extends BaseEntity {

    private Long userId;
    /** wechat/qq/... */
    private String provider;
    private String openId;
    private String unionId;
}
