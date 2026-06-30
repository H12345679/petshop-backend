package com.petshop.controller;

import com.petshop.common.Result;
import com.petshop.security.JwtUtil;
import com.petshop.security.RequireLogin;
import com.petshop.security.UserContext;
import com.petshop.user.model.dto.ChatDTO;
import com.petshop.user.model.vo.ChatHistoryVO;
import com.petshop.user.model.vo.ChatVO;
import com.petshop.user.service.AiChatService;
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

    @ApiOperation("AI 问答客服（免登录亦可调用，携带 Token 则自动关联用户）")
    @PostMapping("/chat")
    public Result<ChatVO> chat(@Valid @RequestBody ChatDTO dto, HttpServletRequest request) {
        Long userId = resolveUserId(request);
        ChatVO vo = aiChatService.chat(userId, dto.getSessionId(), dto.getQuestion());
        return Result.success(vo);
    }

    @ApiOperation("AI 历史对话记录")
    @RequireLogin
    @GetMapping("/chat/history")
    public Result<List<ChatHistoryVO>> history(@RequestParam String sessionId) {
        Long userId = UserContext.getUserId();
        List<ChatHistoryVO> list = aiChatService.getHistory(userId, sessionId);
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
