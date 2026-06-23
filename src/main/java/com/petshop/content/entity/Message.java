package com.petshop.content.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.petshop.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 消息内容（已读状态在 user_message 表，按用户区分） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("message")
public class Message extends BaseEntity {

    private String title;
    private String content;
    /** 1系统 2订单 3活动 4宠物资讯 */
    private Integer type;
    /** 1全体广播 2定向 */
    private Integer scope;
}
