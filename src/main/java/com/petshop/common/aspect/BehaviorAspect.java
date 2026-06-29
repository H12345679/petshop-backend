package com.petshop.common.aspect;

import com.petshop.common.annotation.TrackBehavior;
import com.petshop.content.entity.UserBehavior;
import com.petshop.content.mapper.UserBehaviorMapper;
import com.petshop.security.UserContext;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 用户行为埋点 AOP 切面。
 * 在标注了 @TrackBehavior 的方法成功返回后执行。
 */
@Aspect
@Component
public class BehaviorAspect {

    @Autowired
    private UserBehaviorMapper userBehaviorMapper;

    private final ExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();

    @AfterReturning("@annotation(trackBehavior)")
    public void track(JoinPoint joinPoint, TrackBehavior trackBehavior) {
        try {
            Long userId = UserContext.getUserId();
            if (userId == null) {
                return; // 未登录时不记录
            }

            Long productId = null;
            if (StringUtils.hasText(trackBehavior.productIdSpEL())) {
                productId = parseSpel(joinPoint, trackBehavior.productIdSpEL());
            }

            if (productId != null) {
                UserBehavior behavior = new UserBehavior();
                behavior.setUserId(userId);
                behavior.setProductId(productId);
                behavior.setBehaviorType(trackBehavior.type());
                // 这里为了简单直接插入，在极高并发下可以丢入 MQ 或线程池异步插入
                userBehaviorMapper.insert(behavior);
            }
        } catch (Exception e) {
            // 捕获所有异常，确保埋点逻辑崩溃绝不影响主业务的正常返回
            // 可以加入日志：log.error("用户行为埋点记录失败", e);
        }
    }

    /**
     * 解析 SpEL 表达式
     */
    private Long parseSpel(JoinPoint joinPoint, String spel) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = discoverer.getParameterNames(signature.getMethod());
        Object[] args = joinPoint.getArgs();

        EvaluationContext context = new StandardEvaluationContext();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }
        return parser.parseExpression(spel).getValue(context, Long.class);
    }
}
