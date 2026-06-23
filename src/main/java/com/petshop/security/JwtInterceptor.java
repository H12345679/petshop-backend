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

        RequireLogin requireLogin = getAnnotation(hm, RequireLogin.class);
        RequireRole requireRole = getAnnotation(hm, RequireRole.class);

        // 公开接口直接放行
        if (requireLogin == null && requireRole == null) {
            return true;
        }

        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token == null || token.isEmpty()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        Claims claims;
        try {
            claims = jwtUtil.parseToken(token);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        Long userId = claims.get("userId") == null ? null : Long.valueOf(claims.get("userId").toString());
        String username = claims.get("username", String.class);
        String role = claims.get("role", String.class);
        UserContext.set(new UserContext.LoginUser(userId, username, role));

        if (requireRole != null) {
            boolean allowed = Arrays.asList(requireRole.value()).contains(role);
            if (!allowed) {
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
