package com.petshop.user.model.dto;

import lombok.Data;

import jakarta.validation.constraints.NotNull;

/** 启用/禁用用户请求 */
@Data
public class UserStatusDTO {

    @NotNull(message = "状态不能为空")
    private Integer status; // 1启用 0禁用
}
