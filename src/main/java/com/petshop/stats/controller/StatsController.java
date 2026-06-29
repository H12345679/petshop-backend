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
    @RequireRole("ADMIN")
    @GetMapping("/kpi")
    public Result<Map<String, Object>> kpi() {
        return Result.success(statsService.getKpi());
    }

    @ApiOperation("销量&订单趋势")
    @RequireRole("ADMIN")
    @GetMapping("/sales")
    public Result<Map<String, Object>> salesTrend(@RequestParam(defaultValue = "7") Integer days) {
        return Result.success(statsService.getSalesTrend(days));
    }

    @ApiOperation("订单状态分布")
    @RequireRole("ADMIN")
    @GetMapping("/order-status")
    public Result<List<Map<String, Object>>> orderStatus() {
        return Result.success(statsService.getOrderStatus());
    }

    @ApiOperation("会员等级分布")
    @RequireRole("ADMIN")
    @GetMapping("/member-level")
    public Result<List<Map<String, Object>>> memberLevel() {
        return Result.success(statsService.getMemberLevel());
    }

    @ApiOperation("热销商品 TopN")
    @RequireRole("ADMIN")
    @GetMapping("/product-sales")
    public Result<List<Map<String, Object>>> productSales(@RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(statsService.getProductSales(limit));
    }
}
