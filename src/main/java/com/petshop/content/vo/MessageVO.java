package com.petshop.content.vo;

import com.petshop.content.entity.Message;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息列表 VO，包含用户的已读状态
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MessageVO extends Message {

    /**
     * 0未读 1已读
     */
    private Integer isRead;
}
