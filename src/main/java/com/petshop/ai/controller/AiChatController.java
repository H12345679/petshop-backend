package com.petshop.ai.controller;

import com.petshop.ai.model.dto.ChatDTO;
import com.petshop.ai.model.vo.ChatHistoryVO;
import com.petshop.ai.model.vo.ChatVO;
import com.petshop.ai.service.AiChatService;
import com.petshop.common.Result;
import com.petshop.security.JwtUtil;
import com.petshop.security.RequireLogin;
import com.petshop.security.UserContext;
import com.petshop.user.model.vo.AiSessionVO;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;

/**
 * AI 问答接口（chat 免登录可选关联用户，history 需登录）。
 */
@Tag(name = "05-AI问答")
@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    @Autowired
    private AiChatService aiChatService;

    @Autowired
    private JwtUtil jwtUtil;

    @Operation(summary = "AI 流式问答客服")
    @PostMapping(value = "/chat/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter streamChat(@Valid @RequestBody ChatDTO dto, HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Connection", "keep-alive");
        Long userId = resolveUserId(request);
        return aiChatService.streamChat(userId, dto.getSessionId(), dto.getQuestion());
    }



    @Operation(summary = "AI 历史对话记录")
    @RequireLogin
    @GetMapping("/chat/history")
    public Result<List<ChatHistoryVO>> history(@RequestParam String sessionId) {
        Long userId = UserContext.getUserId();
        List<ChatHistoryVO> list = aiChatService.getHistory(userId, sessionId);
        return Result.success(list);
    }

    @Operation(summary = "AI 历史会话列表")
    @RequireLogin
    @GetMapping("/chat/sessions")
    public Result<List<AiSessionVO>> sessions() {
        Long userId = UserContext.getUserId();
        List<AiSessionVO> list = aiChatService.getSessionList(userId);
        return Result.success(list);
    }

    @Operation(summary = "删除 AI 历史会话")
    @RequireLogin
    @org.springframework.web.bind.annotation.DeleteMapping("/chat/session")
    public Result<Void> deleteSession(@RequestParam String sessionId) {
        Long userId = UserContext.getUserId();
        aiChatService.deleteSession(userId, sessionId);
        return Result.success();
    }

    /** 尝试从请求头解析 Token 获取 userId，解析失败返回 null */
    private Long resolveUserId(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            try {
                Claims claims = jwtUtil.parseToken(token.substring(7));
                return claims.get("userId") == null ? null
                        : Long.valueOf(claims.get("userId").toString());
            } catch (Exception ignored) {
                // 无效 Token 当作匿名处理
            }
        }
        return null;
    }
}
