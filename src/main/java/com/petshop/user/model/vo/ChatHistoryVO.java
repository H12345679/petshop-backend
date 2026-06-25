package com.petshop.user.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** AI 历史对话记录 */
@Data
public class ChatHistoryVO {

    private Long id;
    private String question;
    private String answer;
    private LocalDateTime createTime;
}
