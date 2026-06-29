package com.petshop.ai.service;

import com.petshop.ai.model.vo.ChatHistoryVO;
import com.petshop.ai.model.vo.ChatVO;
import com.petshop.user.model.vo.AiSessionVO;

import java.util.List;

public interface AiChatService {

    /**
     * AI 问答。
     * @param userId 可为 null（匿名提问）
     */
    ChatVO chat(Long userId, String sessionId, String question);

    /** 查询某会话的历史对话记录 */
    List<ChatHistoryVO> getHistory(Long userId, String sessionId);

    /** 查询当前用户的所有历史会话列表 */
    List<AiSessionVO> getSessionList(Long userId);
}
