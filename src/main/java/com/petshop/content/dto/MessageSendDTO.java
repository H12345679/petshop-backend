package com.petshop.content.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 后台发送消息 DTO
 */
@Data
public class MessageSendDTO {

    @NotBlank(message = "标题不能为空")
    private String title;

    @NotBlank(message = "内容不能为空")
    private String content;

    /**
     * 1系统 2订单 3活动 4宠物资讯
     */
    @NotNull(message = "消息类型不能为空")
    private Integer type;

    /**
     * 1全体广播 2定向
     */
    @NotNull(message = "发送范围不能为空")
    private Integer scope;

    /**
     * 定向(scope=2)时必填：目标用户id列表；广播(scope=1)时忽略
     */
    private List<Long> targetUserIds;
}
