package com.petshop.recommend.service;

public interface CollaborativeFilteringService {
    
    /**
     * 执行双路协同过滤算法跑批
     * 1. 抽取 user_item_score 矩阵
     * 2. 计算 User-CF 相似度
     * 3. 计算 Item-CF 相似度
     * 4. 基于加权融合生成推荐列表并入库
     */
    void runCollaborativeFilteringBatch();
}
