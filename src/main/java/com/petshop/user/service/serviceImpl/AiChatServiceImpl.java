package com.petshop.user.service.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.petshop.ai.AiProvider;
import com.petshop.user.entity.AiChatLog;
import com.petshop.user.mapper.AiChatLogMapper;
import com.petshop.user.model.vo.ChatHistoryVO;
import com.petshop.user.model.vo.ChatVO;
import com.petshop.user.service.AiChatService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AiChatServiceImpl implements AiChatService {

    @Autowired
    private AiChatLogMapper aiChatLogMapper;

    @Autowired
    private AiProvider aiProvider;

    @Override
    public ChatVO chat(Long userId, String sessionId, String question) {
        // 调用 AI 生成回答
        String answer = aiProvider.chat(question);

        // 持久化对话记录
        AiChatLog log = new AiChatLog();
        log.setUserId(userId);
        log.setSessionId(sessionId);
        log.setQuestion(question);
        log.setAnswer(answer);
        aiChatLogMapper.insert(log);

        // 构建响应
        ChatVO vo = new ChatVO();
        vo.setSessionId(sessionId);
        vo.setQuestion(question);
        vo.setAnswer(answer);
        return vo;
    }

    @Override
    public List<ChatHistoryVO> getHistory(Long userId, String sessionId) {
        QueryWrapper<AiChatLog> qw = new QueryWrapper<>();
        qw.eq("user_id", userId)
          .eq("session_id", sessionId)
          .orderByAsc("create_time");
        return aiChatLogMapper.selectList(qw).stream().map(log -> {
            ChatHistoryVO vo = new ChatHistoryVO();
            BeanUtils.copyProperties(log, vo);
            return vo;
        }).collect(Collectors.toList());
    }
}
