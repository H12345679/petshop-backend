package com.petshop.recommend.mq;

import com.petshop.config.RabbitMQConfig;
import com.petshop.product.mapper.ProductTagMapper;
import com.petshop.recommend.model.dto.UserBehaviorMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class UserBehaviorListener {

    @Autowired
    private ProductTagMapper productTagMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // Redis key 格式: user_profile:{user_id}:tags
    private static final String REDIS_USER_PROFILE_KEY = "user_profile:%d:tags";

    @RabbitListener(queues = RabbitMQConfig.BEHAVIOR_QUEUE)
    public void handleBehaviorMessage(UserBehaviorMessage message) {
        if (message == null || message.getUserId() == null || message.getProductId() == null) {
            return;
        }

        double score = behaviorScore(message.getBehaviorType());
        if (score < 0) return;

        List<Long> tagIds = productTagMapper.selectTagIdsByProductId(message.getProductId());
        if (tagIds == null || tagIds.isEmpty()) return;

        String redisKey = String.format(REDIS_USER_PROFILE_KEY, message.getUserId());
        for (Long tagId : tagIds) {
            stringRedisTemplate.opsForZSet().incrementScore(redisKey, tagId.toString(), score);
        }

        log.debug("实时更新用户画像成功: userId={}, productId={}, 行为权重={}, 涉及标签={}",
                  message.getUserId(), message.getProductId(), score, tagIds);
    }

    private double behaviorScore(Integer behaviorType) {
        if (behaviorType == null) return -1;
        switch (behaviorType) {
            case 1: return 1.0;
            case 2: return 3.0;
            case 3: return 4.0;
            case 4: return 5.0;
            default: return -1;
        }
    }
}
