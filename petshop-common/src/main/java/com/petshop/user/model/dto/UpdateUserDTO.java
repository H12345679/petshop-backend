package com.petshop.user.model.dto;

import lombok.Data;

/** 修改用户信息请求 */
@Data
public class UpdateUserDTO {

    private String nickname;
    private String avatar;
    private String phone;
    private String email;
    /** 0未知 1男 2女 */
    private Integer gender;
}
