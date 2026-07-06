package com.petshop.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.petshop.ai.AiProvider;
import com.petshop.ai.entity.AiChatLog;
import com.petshop.ai.mapper.AiChatLogMapper;
import com.petshop.ai.model.vo.ChatHistoryVO;
import com.petshop.ai.model.vo.ChatVO;
import com.petshop.ai.service.AiChatService;
import com.petshop.product.entity.Product;
import com.petshop.product.mapper.ProductMapper;
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

    private static final String CREATE_TIME = "create_time";

    @Autowired
    private AiChatLogMapper aiChatLogMapper;

    @Autowired
    private AiProvider aiProvider;

    @Autowired
    private ProductMapper productMapper;

    @Override
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter streamChat(Long userId, String sessionId, String question) {
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(60000L);

        QueryWrapper<Product> qw = new QueryWrapper<>();
        qw.eq("status", 1).orderByDesc(CREATE_TIME).last("LIMIT 30");
        List<Product> productList = productMapper.selectList(qw);
        
        StringBuilder contextBuilder = new StringBuilder();
        for (Product p : productList) {
            contextBuilder.append(String.format("- ID:%d, %s, ￥%s, 简介:%s\n", 
                p.getId(), p.getName(), p.getPrice(), p.getDescription()));
        }

        StringBuilder fullAnswer = new StringBuilder();

        new Thread(() -> {
            try {
                aiProvider.streamChat(question, contextBuilder.toString(),
                    msg -> {
                        try {
                            fullAnswer.append(msg);
                            emitter.send(java.util.Map.of("text", msg));
                        } catch (Exception e) {
                            emitter.completeWithError(e);
                        }
                    },
                    () -> {
                        try {
                            AiChatLog log = new AiChatLog();
                            log.setUserId(userId);
                            log.setSessionId(sessionId);
                            log.setQuestion(question);
                            log.setAnswer(fullAnswer.toString());
                            aiChatLogMapper.insert(log);
                            emitter.complete();
                        } catch (Exception e) {
                            emitter.completeWithError(e);
                        }
                    },
                    error -> {
                        emitter.completeWithError(error);
                    }
                );
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    @Override
    public List<ChatHistoryVO> getHistory(Long userId, String sessionId) {
        QueryWrapper<AiChatLog> qw = new QueryWrapper<>();
        qw.eq("user_id", userId)
          .eq("session_id", sessionId)
          .orderByAsc(CREATE_TIME);
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
        qw.eq("user_id", userId).orderByAsc(CREATE_TIME);
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
