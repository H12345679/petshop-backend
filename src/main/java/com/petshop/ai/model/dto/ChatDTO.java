package com.petshop.ai.model.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/** AI 问答请求 */
@Data
public class ChatDTO {

    @NotBlank(message = "会话ID不能为空")
    private String sessionId;

    @NotBlank(message = "问题不能为空")
    private String question;
}
