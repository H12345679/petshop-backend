package com.petshop.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.petshop.ai.AiProvider;
import com.petshop.ai.entity.AiChatLog;
import com.petshop.ai.mapper.AiChatLogMapper;
import com.petshop.ai.model.vo.ChatHistoryVO;
import com.petshop.ai.model.vo.ChatVO;
import com.petshop.ai.service.AiChatService;
import com.petshop.user.model.vo.AiSessionVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    @Override
    public List<AiSessionVO> getSessionList(Long userId) {
        if (userId == null) {
            return new ArrayList<>();
        }
        QueryWrapper<AiChatLog> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).orderByAsc("create_time");
        List<AiChatLog> allLogs = aiChatLogMapper.selectList(qw);

        Map<String, AiSessionVO> map = new LinkedHashMap<>();
        for (AiChatLog log : allLogs) {
            if (!map.containsKey(log.getSessionId())) {
                AiSessionVO vo = new AiSessionVO();
                vo.setSessionId(log.getSessionId());
                String title = log.getQuestion();
                if (title != null && title.length() > 20) {
                    title = title.substring(0, 20) + "...";
                }
                vo.setTitle(title);
                vo.setCreateTime(log.getCreateTime());
                map.put(log.getSessionId(), vo);
            }
        }
        // 反转列表，使最新创建的会话排在前面
        List<AiSessionVO> list = new ArrayList<>(map.values());
        Collections.reverse(list);
        return list;
    }
}
