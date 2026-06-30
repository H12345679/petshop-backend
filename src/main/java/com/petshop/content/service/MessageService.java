package com.petshop.content.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.petshop.common.PageResult;
import com.petshop.content.dto.MessageSendDTO;
import com.petshop.content.entity.Message;
import com.petshop.content.vo.MessageVO;

public interface MessageService extends IService<Message> {

    /**
     * 发送/推送系统消息（支持广播和定向）
     * @param dto 发送参数
     */
    void sendMessage(MessageSendDTO dto);

    /**
     * 获取后台管理的历史消息分页
     * @param current 页码
     * @param size 每页大小
     * @return 消息分页结果
     */
    PageResult<Message> pageManageMessages(long current, long size);

    /**
     * 获取我的消息列表分页
     * @param current 页码
     * @param size 每页大小
     * @return 消息分页结果（含已读状态）
     */
    PageResult<MessageVO> pageMyMessages(long current, long size);

    /**
     * 标记单条消息为已读
     * @param id 消息ID
     */
    void readMessage(Long id);

    /**
     * 一键已读所有消息
     */
    void readAllMessages();

    /**
     * 统计当前用户未读消息数
     * @return 未读数
     */
    int countUnread();
}
