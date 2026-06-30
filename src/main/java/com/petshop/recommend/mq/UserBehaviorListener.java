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

        Long userId = message.getUserId();
        Long productId = message.getProductId();
        Integer behaviorType = message.getBehaviorType();

        // 1. 根据行为类型定义权重
        double score = 0;
        switch (behaviorType) {
            case 1: score = 1.0; break; // 浏览
            case 2: score = 3.0; break; // 收藏
            case 3: score = 4.0; break; // 加购
            case 4: score = 5.0; break; // 购买
            default: return;
        }

        // 2. 查询该商品有哪些标签
        List<Long> tagIds = productTagMapper.selectTagIdsByProductId(productId);
        if (tagIds == null || tagIds.isEmpty()) {
            return; // 商品没有打标签则忽略
        }

        // 3. 将得分累加到 Redis 的用户画像中 (ZSET)
        String redisKey = String.format(REDIS_USER_PROFILE_KEY, userId);
        for (Long tagId : tagIds) {
            stringRedisTemplate.opsForZSet().incrementScore(redisKey, tagId.toString(), score);
        }

        log.debug("实时更新用户画像成功: userId={}, productId={}, 行为权重={}, 涉及标签={}", 
                  userId, productId, score, tagIds);
    }
}
