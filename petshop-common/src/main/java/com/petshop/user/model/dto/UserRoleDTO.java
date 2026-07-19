package com.petshop.user.model.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/** 变更用户角色请求 */
@Data
public class UserRoleDTO {

    @NotBlank(message = "角色不能为空")
    private String role; // USER / MERCHANT / ADMIN
}
