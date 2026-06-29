package com.petshop.log.aspect;

import com.petshop.log.annotation.LogOperation;
import com.petshop.log.entity.SysLog;
import com.petshop.log.service.SysLogService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petshop.security.JwtUtil;
import io.jsonwebtoken.Claims;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Date;

@Aspect
@Component
public class LogAspect {

    @Autowired
    private SysLogService sysLogService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Pointcut("@annotation(com.petshop.log.annotation.LogOperation)")
    public void logPointCut() {
    }

    @Around("logPointCut()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        long beginTime = System.currentTimeMillis();
        Object result = point.proceed();
        long time = System.currentTimeMillis() - beginTime;
        saveLog(point, time);
        return result;
    }

    private void saveLog(ProceedingJoinPoint joinPoint, long time) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        SysLog sysLog = new SysLog();
        LogOperation logOperation = method.getAnnotation(LogOperation.class);
        if (logOperation != null) {
            sysLog.setOperation(logOperation.value());
        }

        String className = joinPoint.getTarget().getClass().getName();
        String methodName = signature.getName();
        sysLog.setMethod(className + "." + methodName + "()");

        try {
            Object[] args = joinPoint.getArgs();
            if (args != null && args.length > 0) {
                // To avoid serialization errors with Request/Response objects, simply capture first arg if not Request
                Object arg = args[0];
                if (!(arg instanceof HttpServletRequest)) {
                    String params = objectMapper.writeValueAsString(arg);
                    sysLog.setParams(params.length() > 2000 ? params.substring(0, 2000) : params);
                }
            }
        } catch (Exception e) {
            sysLog.setParams("Could not serialize params");
        }

        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        sysLog.setIp(request.getRemoteAddr());

        // Extract user from token
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            try {
                Claims claims = jwtUtil.parseToken(token);
                Long userId = claims.get("userId", Long.class);
                String username = claims.get("username", String.class);
                sysLog.setUserId(userId);
                sysLog.setUsername(username);
            } catch (Exception e) {
                sysLog.setUsername("unknown");
            }
        }

        // Fallback for Login/Register (no token, but we have params)
        if (sysLog.getUsername() == null && sysLog.getParams() != null) {
            try {
                com.fasterxml.jackson.databind.JsonNode jsonNode = objectMapper.readTree(sysLog.getParams());
                if (jsonNode != null && jsonNode.has("username")) {
                    sysLog.setUsername(jsonNode.get("username").asText());
                } else if (jsonNode != null && jsonNode.has("phone")) {
                    sysLog.setUsername(jsonNode.get("phone").asText());
                } else {
                    sysLog.setUsername("未登录用户");
                }
            } catch (Exception e) {
                sysLog.setUsername("未登录用户");
            }
        }

        if (sysLog.getUsername() == null) {
            sysLog.setUsername("未知用户");
        }

        sysLog.setTime(time);
        sysLog.setCreateTime(new Date());

        sysLogService.save(sysLog);
    }
}
