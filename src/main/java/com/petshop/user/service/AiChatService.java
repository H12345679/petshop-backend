package com.petshop.user.service;

import com.petshop.user.model.vo.ChatHistoryVO;
import com.petshop.user.model.vo.ChatVO;

import java.util.List;

public interface AiChatService {

    /**
     * AI 问答。
     * @param userId 可为 null（匿名提问）
     */
    ChatVO chat(Long userId, String sessionId, String question);

    /** 查询某会话的历史对话记录 */
    List<ChatHistoryVO> getHistory(Long userId, String sessionId);
}
