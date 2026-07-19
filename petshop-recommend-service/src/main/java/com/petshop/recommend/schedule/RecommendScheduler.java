package com.petshop.recommend.schedule;

import com.petshop.recommend.service.CollaborativeFilteringService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 推荐相关定时任务。
 * <p>
 * 1) 协同过滤定时跑批：原来 CF 只能靠手动打 /api/recommend/test/run-cf 触发，
 * recommend_result 不会自动刷新。这里默认每 30 分钟跑一次（fixedDelay：上一次跑完
 * 再等 30 分钟，不会重叠），首次在启动 1 分钟后执行。间隔可用配置覆盖：
 * <pre>
 * recommend:
 *   cf:
 *     fixed-delay-ms: 1800000
 *     initial-delay-ms: 60000
 * </pre>
 * 2) 标签画像时间衰减：每天凌晨 3 点把所有用户的 Redis 标签权重 ×0.95
 * （约 14 天半衰），权重低于阈值直接剔除——半年前的浏览不再和昨天的购买同权，
 * 兴趣画像随时间"新陈代谢"（推荐深化 P0）。
 */
@Slf4j
@Component
public class RecommendScheduler {

    @Autowired
    private CollaborativeFilteringService cfService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 每日衰减系数：0.95^14 ≈ 0.49，即两周左右权重减半 */
    private static final double DECAY_FACTOR = 0.95;
    /** 衰减后低于该值的标签直接剔除，防止 ZSET 无限膨胀 */
    private static final double PRUNE_THRESHOLD = 0.3;

    @Scheduled(fixedDelayString = "${recommend.cf.fixed-delay-ms:1800000}",
            initialDelayString = "${recommend.cf.initial-delay-ms:60000}")
    public void scheduledCollaborativeFiltering() {
        try {
            cfService.runCollaborativeFilteringBatch();
        } catch (Exception e) {
            log.error("定时协同过滤跑批失败", e);
        }
    }

    @Scheduled(cron = "${recommend.profile.decay-cron:0 0 3 * * ?}")
    public void scheduledProfileDecay() {
        int keys = 0, pruned = 0;
        try (Cursor<String> cursor = stringRedisTemplate.scan(
                ScanOptions.scanOptions().match("user_profile:*:tags").count(200).build())) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                keys++;
                Set<ZSetOperations.TypedTuple<String>> entries =
                        stringRedisTemplate.opsForZSet().rangeWithScores(key, 0, -1);
                if (entries == null) continue;
                for (ZSetOperations.TypedTuple<String> t : entries) {
                    if (t.getValue() == null || t.getScore() == null) continue;
                    double decayed = t.getScore() * DECAY_FACTOR;
                    if (decayed < PRUNE_THRESHOLD) {
                        stringRedisTemplate.opsForZSet().remove(key, t.getValue());
                        pruned++;
                    } else {
                        stringRedisTemplate.opsForZSet().add(key, t.getValue(), decayed);
                    }
                }
            }
            log.info("标签画像衰减完成：{} 个用户画像，剔除 {} 个低权标签", keys, pruned);
        } catch (Exception e) {
            log.error("标签画像衰减失败", e);
        }
    }
}
