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
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;

/**
 * AI 问答接口（chat 免登录可选关联用户，history 需登录）。
 */
@Api(tags = "05-AI问答")
@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    @Autowired
    private AiChatService aiChatService;

    @Autowired
    private JwtUtil jwtUtil;

    @ApiOperation("AI 流式问答客服")
    @PostMapping(value = "/chat/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter streamChat(@Valid @RequestBody ChatDTO dto, HttpServletRequest request, javax.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Connection", "keep-alive");
        Long userId = resolveUserId(request);
        return aiChatService.streamChat(userId, dto.getSessionId(), dto.getQuestion());
    }



    @ApiOperation("AI 历史对话记录")
    @RequireLogin
    @GetMapping("/chat/history")
    public Result<List<ChatHistoryVO>> history(@RequestParam String sessionId) {
        Long userId = UserContext.getUserId();
        List<ChatHistoryVO> list = aiChatService.getHistory(userId, sessionId);
        return Result.success(list);
    }

    @ApiOperation("AI 历史会话列表")
    @RequireLogin
    @GetMapping("/chat/sessions")
    public Result<List<AiSessionVO>> sessions() {
        Long userId = UserContext.getUserId();
        List<AiSessionVO> list = aiChatService.getSessionList(userId);
        return Result.success(list);
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
