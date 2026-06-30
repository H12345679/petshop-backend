package com.petshop.recommend.service.impl;

import com.petshop.recommend.service.CollaborativeFilteringService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
public class CollaborativeFilteringServiceImpl implements CollaborativeFilteringService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void runCollaborativeFilteringBatch() {
        log.info("开始执行双路协同过滤算法...");
        
        // 1. 读取评分矩阵
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT user_id, product_id, score FROM user_item_score");
        if (rows.isEmpty()) {
            log.warn("没有评分数据，跳过 CF 计算");
            return;
        }

        Map<Long, Map<Long, Double>> userItemMap = new HashMap<>();
        Map<Long, Map<Long, Double>> itemUserMap = new HashMap<>();

        for (Map<String, Object> row : rows) {
            Long userId = ((Number) row.get("user_id")).longValue();
            Long itemId = ((Number) row.get("product_id")).longValue();
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
    }

    /**
     * 计算余弦相似度
     * @param targetToFeatureMap 外层为目标(如 User)，内层为特征(如 Item 及 Score)
     */
    private Map<Long, Map<Long, Double>> calculateSimilarity(Map<Long, Map<Long, Double>> targetToFeatureMap) {
        Map<Long, Map<Long, Double>> simMap = new HashMap<>();
        List<Long> targetIds = new ArrayList<>(targetToFeatureMap.keySet());

        for (int i = 0; i < targetIds.size(); i++) {
            Long targetA = targetIds.get(i);
            Map<Long, Double> featuresA = targetToFeatureMap.get(targetA);
            double normA = calculateNorm(featuresA);

            for (int j = i + 1; j < targetIds.size(); j++) {
                Long targetB = targetIds.get(j);
                Map<Long, Double> featuresB = targetToFeatureMap.get(targetB);
                double normB = calculateNorm(featuresB);

                double dotProduct = 0.0;
                for (Map.Entry<Long, Double> entry : featuresA.entrySet()) {
                    Long featureId = entry.getKey();
                    if (featuresB.containsKey(featureId)) {
                        dotProduct += entry.getValue() * featuresB.get(featureId);
                    }
                }

                double similarity = 0.0;
                if (normA > 0 && normB > 0) {
                    similarity = dotProduct / (normA * normB);
                }

                if (similarity > 0) {
                    simMap.computeIfAbsent(targetA, k -> new HashMap<>()).put(targetB, similarity);
                    simMap.computeIfAbsent(targetB, k -> new HashMap<>()).put(targetA, similarity);
                }
            }
        }
        return simMap;
    }

    private double calculateNorm(Map<Long, Double> features) {
        double sum = 0.0;
        for (Double val : features.values()) {
            sum += val * val;
        }
        return Math.sqrt(sum);
    }

    private void saveUserSimilarity(Map<Long, Map<Long, Double>> userSim) {
        jdbcTemplate.update("TRUNCATE TABLE user_similarity");
        List<Object[]> batchArgs = new ArrayList<>();
        for (Map.Entry<Long, Map<Long, Double>> entryA : userSim.entrySet()) {
            Long uA = entryA.getKey();
            for (Map.Entry<Long, Double> entryB : entryA.getValue().entrySet()) {
                Long uB = entryB.getKey();
                long id = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
                batchArgs.add(new Object[]{id, uA, uB, entryB.getValue()});
            }
        }
        if (!batchArgs.isEmpty()) {
            jdbcTemplate.batchUpdate("INSERT INTO user_similarity (id, user_id, sim_user_id, similarity, update_time) VALUES (?, ?, ?, ?, NOW())", batchArgs);
        }
    }

    private void saveItemSimilarity(Map<Long, Map<Long, Double>> itemSim) {
        jdbcTemplate.update("TRUNCATE TABLE item_similarity");
        List<Object[]> batchArgs = new ArrayList<>();
        for (Map.Entry<Long, Map<Long, Double>> entryA : itemSim.entrySet()) {
            Long iA = entryA.getKey();
            for (Map.Entry<Long, Double> entryB : entryA.getValue().entrySet()) {
                Long iB = entryB.getKey();
                long id = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
                batchArgs.add(new Object[]{id, iA, iB, entryB.getValue()});
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
        jdbcTemplate.update("TRUNCATE TABLE recommend_result");
        List<Object[]> batchArgs = new ArrayList<>();
        List<Long> allItems = new ArrayList<>(itemUserMap.keySet());

        for (Long userId : userItemMap.keySet()) {
            Map<Long, Double> history = userItemMap.get(userId);
            
            // 预测未购买的商品
            for (Long itemId : allItems) {
                if (history.containsKey(itemId)) {
                    continue; // 已经买过的不预测
                }

                // 计算 ICF 预测得分
                double icfScore = 0.0;
                double icfSimSum = 0.0;
                if (itemSim.containsKey(itemId)) {
                    Map<Long, Double> neighbors = itemSim.get(itemId);
                    for (Map.Entry<Long, Double> entry : neighbors.entrySet()) {
                        Long neighborItem = entry.getKey();
                        if (history.containsKey(neighborItem)) {
                            double sim = entry.getValue();
                            icfScore += sim * history.get(neighborItem);
                            icfSimSum += sim;
                        }
                    }
                }
                if (icfSimSum > 0) icfScore /= icfSimSum;

                // 计算 UCF 预测得分
                double ucfScore = 0.0;
                double ucfSimSum = 0.0;
                if (userSim.containsKey(userId)) {
                    Map<Long, Double> neighbors = userSim.get(userId);
                    for (Map.Entry<Long, Double> entry : neighbors.entrySet()) {
                        Long neighborUser = entry.getKey();
                        Map<Long, Double> neighborHistory = userItemMap.get(neighborUser);
                        if (neighborHistory != null && neighborHistory.containsKey(itemId)) {
                            double sim = entry.getValue();
                            ucfScore += sim * neighborHistory.get(itemId);
                            ucfSimSum += sim;
                        }
                    }
                }
                if (ucfSimSum > 0) ucfScore /= ucfSimSum;

                // 混合加权
                double finalScore = 0.6 * icfScore + 0.4 * ucfScore;

                if (finalScore > 0.1) {
                    long id = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
                    batchArgs.add(new Object[]{id, userId, itemId, finalScore, "HYBRID"});
                }
            }
        }

        if (!batchArgs.isEmpty()) {
            // 对推荐结果按 userId 分组，每个用户保留 Top 10，这里为简单起见直接全量插入，或者可以在内存里截断。
            // 内存里截断优化：略，目前直接插入全部预测得分。
            jdbcTemplate.batchUpdate("INSERT INTO recommend_result (id, user_id, product_id, score, source, create_time) VALUES (?, ?, ?, ?, ?, NOW())", batchArgs);
        }
    }
}
