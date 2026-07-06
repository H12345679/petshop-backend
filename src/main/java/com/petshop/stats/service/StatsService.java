package com.petshop.stats.service;

import java.util.List;
import java.util.Map;

/**
 * 后台统计看板 Service 接口
 */
public interface StatsService {
    Map<String, Object> getKpi();
    Map<String, Object> getSalesTrend(Integer days);
    List<Map<String, Object>> getOrderStatus();
    List<Map<String, Object>> getMemberLevel();
    List<Map<String, Object>> getProductSales(Integer limit);
    List<Map<String, Object>> getDailyStats(Integer days);
    Map<String, Object> getLogOps(Integer days);
    List<Map<String, Object>> getShopRanking(Integer limit);
}
