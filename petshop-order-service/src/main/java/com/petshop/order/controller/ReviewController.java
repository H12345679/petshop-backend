package com.petshop.order.controller;

import com.petshop.common.PageResult;
import com.petshop.common.Result;
import com.petshop.log.annotation.LogOperation;
import com.petshop.order.entity.Review;
import com.petshop.order.service.ReviewService;
import com.petshop.security.RequireLogin;
import com.petshop.security.RequireRole;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 评价接口（对应《项目接口设计文档》C 模块第 21~25 节）。
 */
@Tag(name = "08-评价")
@RestController
@RequestMapping("/api")
public class ReviewController {

    @Autowired
    private ReviewService reviewService;

    // ==================== 用户 ====================

    @Operation(summary = "提交评价(仅状态 3 订单,全部明细评价后订单→4)")
    @RequireLogin
    @PostMapping("/reviews")
    public Result<Review> submit(@RequestBody Review review) {
        return Result.success(reviewService.submitReview(review));
    }

    @Operation(summary = "获取当前用户自己的评价")
    @RequireLogin
    @GetMapping("/reviews/my")
    public Result<PageResult<Map<String, Object>>> myReviews(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(reviewService.myReviews(current, size));
    }

    // ==================== 商家/管理员 ====================

    @Operation(summary = "商家回复评价(ADMIN·MERCHANT 本店)")
    @RequireRole({"ADMIN", "MERCHANT"})
    @PutMapping("/reviews/{id}/reply")
    public Result<Void> reply(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String reply = (String) body.get("reply");
        reviewService.reply(id, reply);
        return Result.success();
    }

    @Operation(summary = "后台评价管理列表(ADMIN 全站 / MERCHANT 仅本店)")
    @RequireRole({"ADMIN", "MERCHANT"})
    @GetMapping("/reviews/manage")
    public Result<PageResult<Map<String, Object>>> manage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) Integer hasReply,
            @RequestParam(required = false) Integer showDeleted) {
        return Result.success(reviewService.managePage(current, size, productId, rating, hasReply, showDeleted));
    }

    @Operation(summary = "删除违规评价(仅 ADMIN,逻辑删除)")
    @RequireRole("ADMIN")
    @LogOperation("删除评价")
    @DeleteMapping("/reviews/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        reviewService.deleteReview(id);
        return Result.success();
    }

    @Operation(summary = "恢复已删除评价(仅 ADMIN)")
    @RequireRole("ADMIN")
    @LogOperation("恢复评价")
    @PutMapping("/reviews/{id}/restore")
    public Result<Void> restore(@PathVariable Long id) {
        reviewService.restoreReview(id);
        return Result.success();
    }

    // ==================== 公开（挂载在商品路径下） ====================

    @Operation(summary = "商品评价列表(公开,分页)")
    @GetMapping("/products/{productId}/reviews")
    public Result<PageResult<Review>> productReviews(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(reviewService.productReviews(productId, current, size));
    }
}
