package com.petshop.log.aspect;

import com.petshop.log.annotation.LogOperation;
import com.petshop.log.entity.SysLog;
import com.petshop.log.service.SysLogService;
import com.fasterxml.jackson.databind.JsonNode;
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

    private static final String USERNAME = "username";

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

        extractParams(joinPoint, sysLog);

        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        sysLog.setIp(request.getRemoteAddr());

        extractUserInfo(request, sysLog);

        sysLog.setTime(time);
        sysLog.setCreateTime(new Date());
        sysLogService.save(sysLog);
    }

    private void extractParams(ProceedingJoinPoint joinPoint, SysLog sysLog) {
        try {
            Object[] args = joinPoint.getArgs();
            if (args != null && args.length > 0 && !(args[0] instanceof HttpServletRequest)) {
                String params = objectMapper.writeValueAsString(args[0]);
                sysLog.setParams(params.length() > 2000 ? params.substring(0, 2000) : params);
            }
        } catch (Exception e) {
            sysLog.setParams("无法序列化参数");
        }
    }

    private void extractUserInfo(HttpServletRequest request, SysLog sysLog) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            try {
                Claims claims = jwtUtil.parseToken(token);
                sysLog.setUserId(claims.get("userId", Long.class));
                sysLog.setUsername(claims.get(USERNAME, String.class));
            } catch (Exception e) {
                sysLog.setUsername("未知用户");
            }
        }

        if (sysLog.getUsername() == null && sysLog.getParams() != null) {
            try {
                JsonNode jsonNode = objectMapper.readTree(sysLog.getParams());
                if (jsonNode != null && jsonNode.has(USERNAME)) {
                    sysLog.setUsername(jsonNode.get(USERNAME).asText());
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
    }
}
