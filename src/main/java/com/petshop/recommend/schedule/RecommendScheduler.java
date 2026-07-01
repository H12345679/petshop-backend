package com.petshop.recommend.schedule;

import com.petshop.recommend.service.CollaborativeFilteringService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 协同过滤定时跑批。
 * <p>
 * 原来 CF 只能靠手动打 /api/recommend/test/run-cf 触发，recommend_result 不会自动刷新。
 * 这里默认每 30 分钟跑一次（fixedDelay：上一次跑完再等 30 分钟，不会重叠），
 * 首次在启动 1 分钟后执行。间隔可用配置覆盖：
 * <pre>
 * recommend:
 *   cf:
 *     fixed-delay-ms: 1800000
 *     initial-delay-ms: 60000
 * </pre>
 */
@Slf4j
@Component
public class RecommendScheduler {

    @Autowired
    private CollaborativeFilteringService cfService;

    @Scheduled(fixedDelayString = "${recommend.cf.fixed-delay-ms:1800000}",
            initialDelayString = "${recommend.cf.initial-delay-ms:60000}")
    public void scheduledCollaborativeFiltering() {
        try {
            cfService.runCollaborativeFilteringBatch();
        } catch (Exception e) {
            log.error("定时协同过滤跑批失败", e);
        }
    }
}
