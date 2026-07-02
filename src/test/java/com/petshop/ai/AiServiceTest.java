package com.petshop.ai;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.petshop.ai.entity.AiChatLog;
import com.petshop.ai.mapper.AiChatLogMapper;
import com.petshop.ai.model.vo.ChatHistoryVO;
import com.petshop.ai.service.impl.AiChatServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AiServiceTest {

    @InjectMocks
    private AiChatServiceImpl aiChatService;

    @Mock
    private AiChatLogMapper aiChatLogMapper;

    @Test
    public void testGetHistory_Empty() {
        when(aiChatLogMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        
        List<ChatHistoryVO> history = aiChatService.getHistory(1L, "session123");
        
        assertEquals(0, history.size());
    }
}
