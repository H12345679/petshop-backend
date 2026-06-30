package com.petshop.order.service;

import com.petshop.common.PageResult;
import com.petshop.order.entity.Review;

import java.util.Map;

/**
 * 评价服务接口（对应《项目接口设计文档》C 模块第 21~25 节）。
 */
public interface ReviewService {

    /** 提交评价（仅状态 3，全部明细评价后订单→4）。 */
    Review submitReview(Review review);

    /** 商家回复评价（ADMIN·MERCHANT 本店）。 */
    void reply(Long reviewId, String reply);

    /** 查询某商品的所有评价（公开，分页）。 */
    PageResult<Review> productReviews(Long productId, int current, int size);

    /** 后台评价管理列表（ADMIN 全站 / MERCHANT 仅本店，showDeleted=1 查已删除）。 */
    PageResult<Map<String, Object>> managePage(int current, int size, Long productId,
                                                Integer rating, Integer hasReply,
                                                Integer showDeleted);

    /** 删除违规评价（仅 ADMIN，逻辑删除）。 */
    void deleteReview(Long reviewId);

    /** 恢复已删除评价（仅 ADMIN）。 */
    void restoreReview(Long reviewId);
}
