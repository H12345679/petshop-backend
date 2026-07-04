package com.petshop.security;

import com.petshop.common.BusinessException;
import com.petshop.common.ResultCode;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.annotation.Annotation;
import java.util.Arrays;

/**
 * JWT 鉴权拦截器：
 * - 接口未加 @RequireLogin / @RequireRole -> 视为公开接口，放行；
 * - 加了注解 -> 校验 token，解析出登录人放入 UserContext；
 * - @RequireRole -> 额外校验角色。
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod hm = (HandlerMethod) handler;

        // 尝试解析 Token（无论是否强制要求登录，有合法 Token 就把用户信息放入上下文）
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token != null && !token.isEmpty()) {
            try {
                Claims claims = jwtUtil.parseToken(token);
                Long userId = claims.get("userId") == null ? null : Long.valueOf(claims.get("userId").toString());
                String username = claims.get("username", String.class);
                String role = claims.get("role", String.class);
                UserContext.set(new UserContext.LoginUser(userId, username, role));
            } catch (Exception e) {
                // Token 无效：对公开接口宽容（忽略无效 token），对强制登录接口严格
            }
        }

        RequireLogin requireLogin = getAnnotation(hm, RequireLogin.class);
        RequireRole requireRole = getAnnotation(hm, RequireRole.class);

        // 公开接口直接放行（即使无 token / token 无效也放行）
        if (requireLogin == null && requireRole == null) {
            return true;
        }

        // 强制登录接口：必须有合法 token
        if (UserContext.getUserId() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        // 从 ThreadLocal (全局上下文) 中获取当前登录用户的角色，比如 "USER", "ADMIN", "MERCHANT"
        String role = UserContext.getRole();
        
        // 如果当前访问的接口贴了 @RequireRole 标签（说明这是一个需要特定角色才能访问的接口）
        if (requireRole != null) {
            // requireRole.value() 拿到的是标签里允许的角色数组，比如 {"ADMIN", "MERCHANT"}
            // 将其转成 List，然后检查当前用户的 role 在不在这个允许的列表里
            boolean allowed = role != null && Arrays.asList(requireRole.value()).contains(role);
            
            // 如果用户的角色不在允许列表里（也就是 allowed 为 false）
            if (!allowed) {
                // 抛出 403 异常，拒绝访问。
                // 此时前端会收到类似 "无权访问该资源" 的提示。
                throw new BusinessException(ResultCode.FORBIDDEN);
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private <A extends Annotation> A getAnnotation(HandlerMethod hm, Class<A> clazz) {
        A a = hm.getMethodAnnotation(clazz);
        if (a == null) {
            a = hm.getBeanType().getAnnotation(clazz);
        }
        return a;
    }
}
