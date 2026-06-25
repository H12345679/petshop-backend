package com.petshop.user.model.vo;

import lombok.Data;

/** AI 问答响应 */
@Data
public class ChatVO {

    private String sessionId;
    private String question;
    private String answer;
}
