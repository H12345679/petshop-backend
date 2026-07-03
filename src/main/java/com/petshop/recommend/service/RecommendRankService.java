package com.petshop.recommend.service;

import com.petshop.product.entity.Product;

import java.util.List;

/**
 * 首页个性化推荐——多路召回 + 融合排序（推荐深化 P0）。
 * <p>
 * 在协同过滤/标签画像两路行为信号之外，引入用户个人信息：
 * 宠物档案（user_pet→标签匹配 + 物种冲突过滤）、性别/养宠人群热度。
 * <pre>
 * finalScore = 0.40×CF + 0.25×标签画像 + 0.20×宠物匹配 + 0.15×人群热度 − 惩罚(近30天已购)
 * 重排：同类目最多 2 个（打散）；有宠物档案时过滤物种冲突商品（猫用户不出狗主粮）
 * </pre>
 */
public interface RecommendRankService {

    /**
     * 为用户生成个性化推荐（带 recommendReason 推荐理由）。
     * @return 最多 n 条；用户完全无信号（无行为、无档案）时返回空列表，由调用方兜底
     */
    List<Product> rankForUser(Long userId, int n);
}
