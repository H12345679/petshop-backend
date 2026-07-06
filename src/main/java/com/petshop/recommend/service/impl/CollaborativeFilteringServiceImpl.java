package com.petshop.recommend.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.petshop.recommend.service.CollaborativeFilteringService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class CollaborativeFilteringServiceImpl implements CollaborativeFilteringService {

    private static final String USER_ID = "user_id";
    private static final String PRODUCT_ID = "product_id";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final int TOP_N_PER_USER = 20;

    /** 防止定时任务与手动触发并发跑批（会互相清表） */
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Override
    @Transactional
    public void runCollaborativeFilteringBatch() {
        if (!running.compareAndSet(false, true)) {
            log.warn("CF 跑批已在进行中，跳过本次触发");
            return;
        }
        try {
            log.info("开始执行双路协同过滤算法...");

            // 0. 先把真实用户行为聚合进评分矩阵，否则 CF 只对 mock 数据有效
            syncScoresFromBehavior();

            // 1. 读取评分矩阵
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT user_id, product_id, score FROM user_item_score");
            if (rows.isEmpty()) {
                log.warn("没有评分数据，跳过 CF 计算");
                return;
            }

            Map<Long, Map<Long, Double>> userItemMap = new HashMap<>();
            Map<Long, Map<Long, Double>> itemUserMap = new HashMap<>();
            for (Map<String, Object> row : rows) {
                Long userId = ((Number) row.get(USER_ID)).longValue();
                Long itemId = ((Number) row.get(PRODUCT_ID)).longValue();
                Double score = ((Number) row.get("score")).doubleValue();
                userItemMap.computeIfAbsent(userId, k -> new HashMap<>()).put(itemId, score);
                itemUserMap.computeIfAbsent(itemId, k -> new HashMap<>()).put(userId, score);
            }

            // 2. 计算 User-CF 相似度
            Map<Long, Map<Long, Double>> userSim = calculateSimilarity(userItemMap);
            saveUserSimilarity(userSim);

            // 3. 计算 Item-CF 相似度
            Map<Long, Map<Long, Double>> itemSim = calculateSimilarity(itemUserMap);
            saveItemSimilarity(itemSim);

            // 4. 生成双路召回推荐结果 (混合权重 ICF=0.6, UCF=0.4)
            generateAndSaveRecommendations(userItemMap, itemUserMap, userSim, itemSim);

            log.info("双路协同过滤算法执行完毕！");
        } finally {
            running.set(false);
        }
    }

    /**
     * 把 user_behavior 按行为类型加权聚合成隐式评分，upsert 进 user_item_score。
     * 权重与实时画像一致：浏览1 / 收藏3 / 加购4 / 购买5。
     * 用 uk_user_product 唯一键做 ON DUPLICATE KEY UPDATE，不会动没有行为的 mock 用户数据。
     */
    private void syncScoresFromBehavior() {
        List<Map<String, Object>> agg = jdbcTemplate.queryForList(
                "SELECT user_id, product_id, " +
                "SUM(CASE behavior_type WHEN 1 THEN 1 WHEN 2 THEN 3 WHEN 3 THEN 4 WHEN 4 THEN 5 ELSE 0 END) AS score " +
                "FROM user_behavior WHERE deleted = 0 GROUP BY user_id, product_id");

        List<Object[]> args = new ArrayList<>();
        for (Map<String, Object> row : agg) {
            Object scoreObj = row.get("score");
            if (scoreObj != null && ((Number) scoreObj).doubleValue() > 0) {
                double score = ((Number) scoreObj).doubleValue();
                Long userId = ((Number) row.get(USER_ID)).longValue();
                Long productId = ((Number) row.get(PRODUCT_ID)).longValue();
                args.add(new Object[]{IdWorker.getId(), userId, productId, score});
            }
        }
        if (!args.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    "INSERT INTO user_item_score (id, user_id, product_id, score, update_time) VALUES (?, ?, ?, ?, NOW()) " +
                    "ON DUPLICATE KEY UPDATE score = VALUES(score), update_time = NOW()", args);
        }
        log.info("已从 user_behavior 聚合 {} 条真实评分 upsert 进 user_item_score", args.size());
    }

    /**
     * 计算余弦相似度。外层为目标(如 User)，内层为特征(如 Item 及 Score)。
     */
    private Map<Long, Map<Long, Double>> calculateSimilarity(Map<Long, Map<Long, Double>> targetToFeatureMap) {
        Map<Long, Map<Long, Double>> simMap = new HashMap<>();
        List<Long> targetIds = new ArrayList<>(targetToFeatureMap.keySet());

        Map<Long, Double> normMap = new HashMap<>();
        for (Long id : targetIds) {
            normMap.put(id, calculateNorm(targetToFeatureMap.get(id)));
        }

        for (int i = 0; i < targetIds.size(); i++) {
            Long targetA = targetIds.get(i);
            double normA = normMap.get(targetA);
            if (normA <= 0) {
                continue;
            }
            Map<Long, Double> featuresA = targetToFeatureMap.get(targetA);
            computePairwiseSimilarity(targetIds, i, featuresA, normA, normMap, targetToFeatureMap, simMap);
        }
        return simMap;
    }

    private void computePairwiseSimilarity(List<Long> targetIds, int i, Map<Long, Double> featuresA, double normA,
                                           Map<Long, Double> normMap, Map<Long, Map<Long, Double>> targetToFeatureMap,
                                           Map<Long, Map<Long, Double>> simMap) {
        Long targetA = targetIds.get(i);
        for (int j = i + 1; j < targetIds.size(); j++) {
            Long targetB = targetIds.get(j);
            double normB = normMap.get(targetB);
            if (normB <= 0) {
                continue;
            }
            Map<Long, Double> featuresB = targetToFeatureMap.get(targetB);
            double dotProduct = calculateDotProduct(featuresA, featuresB);
            double similarity = dotProduct / (normA * normB);
            if (similarity > 0) {
                simMap.computeIfAbsent(targetA, k -> new HashMap<>()).put(targetB, similarity);
                simMap.computeIfAbsent(targetB, k -> new HashMap<>()).put(targetA, similarity);
            }
        }
    }

    private double calculateDotProduct(Map<Long, Double> featuresA, Map<Long, Double> featuresB) {
        Map<Long, Double> small = featuresA.size() <= featuresB.size() ? featuresA : featuresB;
        Map<Long, Double> large = (small == featuresA) ? featuresB : featuresA;
        double dotProduct = 0.0;
        for (Map.Entry<Long, Double> entry : small.entrySet()) {
            Double v = large.get(entry.getKey());
            if (v != null) {
                dotProduct += entry.getValue() * v;
            }
        }
        return dotProduct;
    }

    private double calculateNorm(Map<Long, Double> features) {
        double sum = 0.0;
        for (Double val : features.values()) {
            sum += val * val;
        }
        return Math.sqrt(sum);
    }

    private void saveUserSimilarity(Map<Long, Map<Long, Double>> userSim) {
        // DELETE 而非 TRUNCATE：TRUNCATE 是 DDL 会隐式提交，破坏 @Transactional 原子性
        jdbcTemplate.update("DELETE FROM user_similarity");
        List<Object[]> batchArgs = new ArrayList<>();
        for (Map.Entry<Long, Map<Long, Double>> entryA : userSim.entrySet()) {
            Long uA = entryA.getKey();
            for (Map.Entry<Long, Double> entryB : entryA.getValue().entrySet()) {
                batchArgs.add(new Object[]{IdWorker.getId(), uA, entryB.getKey(), entryB.getValue()});
            }
        }
        if (!batchArgs.isEmpty()) {
            jdbcTemplate.batchUpdate("INSERT INTO user_similarity (id, user_id, sim_user_id, similarity, update_time) VALUES (?, ?, ?, ?, NOW())", batchArgs);
        }
    }

    private void saveItemSimilarity(Map<Long, Map<Long, Double>> itemSim) {
        jdbcTemplate.update("DELETE FROM item_similarity");
        List<Object[]> batchArgs = new ArrayList<>();
        for (Map.Entry<Long, Map<Long, Double>> entryA : itemSim.entrySet()) {
            Long iA = entryA.getKey();
            for (Map.Entry<Long, Double> entryB : entryA.getValue().entrySet()) {
                batchArgs.add(new Object[]{IdWorker.getId(), iA, entryB.getKey(), entryB.getValue()});
            }
        }
        if (!batchArgs.isEmpty()) {
            jdbcTemplate.batchUpdate("INSERT INTO item_similarity (id, product_id, sim_product_id, similarity, update_time) VALUES (?, ?, ?, ?, NOW())", batchArgs);
        }
    }

    private void generateAndSaveRecommendations(Map<Long, Map<Long, Double>> userItemMap,
                                                Map<Long, Map<Long, Double>> itemUserMap,
                                                Map<Long, Map<Long, Double>> userSim,
                                                Map<Long, Map<Long, Double>> itemSim) {
        jdbcTemplate.update("DELETE FROM recommend_result");
        List<Object[]> batchArgs = new ArrayList<>();
        List<Long> allItems = new ArrayList<>(itemUserMap.keySet());

        Map<Long, Set<Long>> purchasedMap = loadPurchasedMap();

        for (Map.Entry<Long, Map<Long, Double>> userEntry : userItemMap.entrySet()) {
            Long userId = userEntry.getKey();
            Map<Long, Double> history = userEntry.getValue();
            Set<Long> purchasedSet = purchasedMap.getOrDefault(userId, Collections.emptySet());

            List<Map.Entry<Long, Double>> candidates = scoreCandidates(
                    userId, history, purchasedSet, allItems, itemSim, userSim, userItemMap);

            candidates.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            int limit = Math.min(TOP_N_PER_USER, candidates.size());
            for (int k = 0; k < limit; k++) {
                Map.Entry<Long, Double> c = candidates.get(k);
                batchArgs.add(new Object[]{IdWorker.getId(), userId, c.getKey(), c.getValue(), "HYBRID"});
            }
        }

        if (!batchArgs.isEmpty()) {
            jdbcTemplate.batchUpdate("INSERT INTO recommend_result (id, user_id, product_id, score, source, create_time) VALUES (?, ?, ?, ?, ?, NOW())", batchArgs);
        }
    }

    private Map<Long, Set<Long>> loadPurchasedMap() {
        Map<Long, Set<Long>> purchasedMap = new HashMap<>();
        List<Map<String, Object>> buyRows = jdbcTemplate.queryForList(
                "SELECT user_id, product_id FROM user_behavior WHERE behavior_type = 4 AND deleted = 0");
        for (Map<String, Object> row : buyRows) {
            Long userId = ((Number) row.get(USER_ID)).longValue();
            Long itemId = ((Number) row.get(PRODUCT_ID)).longValue();
            purchasedMap.computeIfAbsent(userId, k -> new HashSet<>()).add(itemId);
        }
        return purchasedMap;
    }

    private List<Map.Entry<Long, Double>> scoreCandidates(Long userId, Map<Long, Double> history,
                                                          Set<Long> purchasedSet, List<Long> allItems,
                                                          Map<Long, Map<Long, Double>> itemSim,
                                                          Map<Long, Map<Long, Double>> userSim,
                                                          Map<Long, Map<Long, Double>> userItemMap) {
        List<Map.Entry<Long, Double>> candidates = new ArrayList<>();
        for (Long itemId : allItems) {
            if (purchasedSet.contains(itemId)) {
                continue;
            }
            double icfScore = predictIcfScore(itemId, history, itemSim);
            double ucfScore = predictUcfScore(userId, itemId, userSim, userItemMap);
            double finalScore = 0.6 * icfScore + 0.4 * ucfScore;

            Double existingScore = history.get(itemId);
            if (existingScore != null && existingScore > 0) {
                finalScore *= 1.2;
            }
            if (finalScore > 0.1) {
                candidates.add(new AbstractMap.SimpleEntry<>(itemId, finalScore));
            }
        }
        return candidates;
    }

    private double predictIcfScore(Long itemId, Map<Long, Double> history, Map<Long, Map<Long, Double>> itemSim) {
        double icfScore = 0.0;
        double icfSimSum = 0.0;
        Map<Long, Double> itemNeighbors = itemSim.get(itemId);
        if (itemNeighbors != null) {
            for (Map.Entry<Long, Double> entry : itemNeighbors.entrySet()) {
                Double h = history.get(entry.getKey());
                if (h != null) {
                    icfScore += entry.getValue() * h;
                    icfSimSum += entry.getValue();
                }
            }
        }
        return icfSimSum > 0 ? icfScore / icfSimSum : 0.0;
    }

    private double predictUcfScore(Long userId, Long itemId, Map<Long, Map<Long, Double>> userSim,
                                   Map<Long, Map<Long, Double>> userItemMap) {
        double ucfScore = 0.0;
        double ucfSimSum = 0.0;
        Map<Long, Double> userNeighbors = userSim.get(userId);
        if (userNeighbors != null) {
            for (Map.Entry<Long, Double> entry : userNeighbors.entrySet()) {
                Map<Long, Double> neighborHistory = userItemMap.get(entry.getKey());
                Double h = (neighborHistory == null) ? null : neighborHistory.get(itemId);
                if (h != null) {
                    ucfScore += entry.getValue() * h;
                    ucfSimSum += entry.getValue();
                }
            }
        }
        return ucfSimSum > 0 ? ucfScore / ucfSimSum : 0.0;
    }
}
