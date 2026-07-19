package com.petshop.content.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.petshop.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 用户消息已读状态（每个用户对每条消息独立已读） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_message")
public class UserMessage extends BaseEntity {

    private Long messageId;
    private Long userId;
    /** 0未读 1已读 */
    private Integer isRead;
    private LocalDateTime readTime;
}
