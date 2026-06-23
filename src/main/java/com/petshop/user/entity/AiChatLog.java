package com.petshop.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.petshop.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** AI 问答记录 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_chat_log")
public class AiChatLog extends BaseEntity {

    private Long userId;
    private String sessionId;
    private String question;
    private String answer;
}
