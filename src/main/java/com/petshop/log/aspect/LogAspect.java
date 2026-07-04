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
        // 1. 获取被拦截方法的基本信息（类名、方法名）
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        SysLog sysLog = new SysLog();
        
        // 2. 尝试获取该方法上的 @LogOperation 注解。
        // 这个注解是开发者手动贴上去的，里面的 value 比如 "新增视频" 就是操作描述。
        LogOperation logOperation = method.getAnnotation(LogOperation.class);
        if (logOperation != null) {
            sysLog.setOperation(logOperation.value());
        }

        // 3. 拼接完整的类名和方法名，比如 "com.petshop.content.VideoController.createVideo()"
        String className = joinPoint.getTarget().getClass().getName();
        String methodName = signature.getName();
        sysLog.setMethod(className + "." + methodName + "()");

        // 4. 提取并保存请求参数（前端传给接口的各种数据）
        try {
            Object[] args = joinPoint.getArgs();
            if (args != null && args.length > 0) {
                // 为避免 Request/Response 对象的序列化错误，如果第一个参数不是 Request，则尝试将其转换成 JSON 字符串
                Object arg = args[0];
                if (!(arg instanceof HttpServletRequest)) {
                    String params = objectMapper.writeValueAsString(arg);
                    // 数据库字段有长度限制，如果参数太长（超过 2000 个字符）就截断，防止报错
                    sysLog.setParams(params.length() > 2000 ? params.substring(0, 2000) : params);
                }
            }
        } catch (Exception e) {
            sysLog.setParams("无法序列化参数");
        }

        // 5. 通过 Spring 上下文拿到当前的 HTTP 请求对象，从而获取用户的 IP 地址
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        sysLog.setIp(request.getRemoteAddr());

        // 6. 从 Token 中提取正在操作的用户信息（知道是谁在干这件事）
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
                sysLog.setUsername("未知用户");
            }
        }

        // 7. 特殊场景补充处理：如果是“登录/注册”接口，此时用户根本没有 Token！
        // 但我们需要知道是谁在登录，所以从刚才第4步保存的请求参数 JSON 里“偷窥”一下 username 或 phone。
        if (sysLog.getUsername() == null && sysLog.getParams() != null) {
            try {
                JsonNode jsonNode = objectMapper.readTree(sysLog.getParams());
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

        // 兜底：如果实在拿不到用户名，就写“未知用户”
        if (sysLog.getUsername() == null) {
            sysLog.setUsername("未知用户");
        }

        // 8. 记录方法执行花费的时间（毫秒）和当前创建时间
        sysLog.setTime(time);
        sysLog.setCreateTime(new Date());

        // 9. 将组装好的这只 SysLog 对象插入到数据库中
        sysLogService.save(sysLog);
    }
}
