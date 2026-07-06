package com.petshop.stats.controller;

import com.petshop.common.Result;
import com.petshop.security.RequireRole;
import com.petshop.stats.service.StatsService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Api(tags = "管理后台-统计看板")
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    @Autowired
    private StatsService statsService;

    @ApiOperation("KPI 概览")
    @RequireRole({"ADMIN", "MERCHANT"})
    @GetMapping("/kpi")
    public Result<Map<String, Object>> kpi() {
        return Result.success(statsService.getKpi());
    }

    @ApiOperation("销量&订单趋势")
    @RequireRole({"ADMIN", "MERCHANT"})
    @GetMapping("/sales")
    public Result<Map<String, Object>> salesTrend(@RequestParam(defaultValue = "7") Integer days) {
        return Result.success(statsService.getSalesTrend(days));
    }

    @ApiOperation("订单状态分布")
    @RequireRole({"ADMIN", "MERCHANT"})
    @GetMapping("/order-status")
    public Result<List<Map<String, Object>>> orderStatus() {
        return Result.success(statsService.getOrderStatus());
    }

    @ApiOperation("会员等级分布")
    @RequireRole({"ADMIN", "MERCHANT"})
    @GetMapping("/member-level")
    public Result<List<Map<String, Object>>> memberLevel() {
        return Result.success(statsService.getMemberLevel());
    }

    @ApiOperation("热销商品 TopN")
    @RequireRole({"ADMIN", "MERCHANT"})
    @GetMapping("/product-sales")
    public Result<List<Map<String, Object>>> productSales(@RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(statsService.getProductSales(limit));
    }

    @ApiOperation("历史日度聚合统计")
    @RequireRole({"ADMIN"})
    @GetMapping("/daily")
    public Result<List<Map<String, Object>>> dailyStats(@RequestParam(defaultValue = "30") Integer days) {
        return Result.success(statsService.getDailyStats(days));
    }

    @ApiOperation("操作日志统计")
    @RequireRole({"ADMIN"})
    @GetMapping("/log-ops")
    public Result<Map<String, Object>> logOps(@RequestParam(defaultValue = "7") Integer days) {
        return Result.success(statsService.getLogOps(days));
    }

    @ApiOperation("店铺商品销量排行 TopN")
    @RequireRole({"ADMIN", "MERCHANT"})
    @GetMapping("/shop-ranking")
    public Result<List<Map<String, Object>>> shopRanking(@RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(statsService.getShopRanking(limit));
    }
}
