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
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }

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

        String role = UserContext.getRole();
        if (requireRole != null) {
            if (role == null || !Arrays.asList(requireRole.value()).contains(role)) {
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
