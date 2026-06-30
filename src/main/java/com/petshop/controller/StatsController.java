package com.petshop.controller;

import com.petshop.common.Result;
import com.petshop.security.RequireRole;
import com.petshop.security.UserContext;
import com.petshop.shop.entity.Shop;
import com.petshop.shop.service.ShopService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据看板统计接口（ADMIN 看全局，MERCHANT 看自己店铺）。
 */
@Api(tags = "09-数据看板")
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ShopService shopService;

    /** 获取当前 MERCHANT 管理的店铺 ID 列表；
     *  返回 null → ADMIN（不过滤）；返回空列表 → MERCHANT 无店铺（显示零值）；返回非空 → 按店铺过滤 */
    private List<Long> getMerchantShopIds() {
        String role = UserContext.getRole();
        if (!"MERCHANT".equals(role)) return null; // ADMIN → null 表示全局
        Long userId = UserContext.getUserId();
        List<Shop> shops = shopService.lambdaQuery().eq(Shop::getOwnerId, userId).list();
        if (shops == null || shops.isEmpty()) {
            return Collections.emptyList(); // MERCHANT 无店铺 → 空列表表示全零
        }
        return shops.stream().map(Shop::getId).collect(Collectors.toList());
    }

    /** 拼接 shop_id 过滤条件前缀（含 AND）。shopIds=null 时返回空串（ADMIN 全局） */
    private String shopFilter(List<Long> shopIds, String tableAlias) {
        if (shopIds == null || shopIds.isEmpty()) return "";
        String prefix = tableAlias == null ? "" : tableAlias + ".";
        return " AND " + prefix + "shop_id IN (" +
                shopIds.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")";
    }

    /** shopIds=null → 不限制（ADMIN）；空列表 → 条件永假（MERCHANT 无店铺） */
    private String shopFilterForZeroable(List<Long> shopIds, String tableAlias) {
        if (shopIds == null) return ""; // ADMIN → 不限制
        String prefix = tableAlias == null ? "" : tableAlias + ".";
        if (shopIds.isEmpty()) {
            return " AND " + prefix + "shop_id < 0"; // 永假条件 → 返回零结果
        }
        return " AND " + prefix + "shop_id IN (" +
                shopIds.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")";
    }

    @ApiOperation("KPI 概览：今日营业额、订单数、总用户/商品（MERCHANT 按店铺隔离）")
    @RequireRole({"ADMIN", "MERCHANT"})
    @GetMapping("/kpi")
    public Result<Map<String, Object>> kpi() {
        List<Long> shopIds = getMerchantShopIds();
        String sf = shopFilterForZeroable(shopIds, "o");

        Map<String, Object> data = new LinkedHashMap<>();

        // 今日营业额（已完成订单，按店铺过滤）
        BigDecimal todayRevenue = jdbc.queryForObject(
                "SELECT COALESCE(SUM(pay_amount), 0) FROM orders o WHERE status = 4 AND DATE(create_time) = CURDATE()" + sf,
                BigDecimal.class);
        data.put("todayRevenue", todayRevenue);

        // 昨日营业额（用于计算涨幅）
        BigDecimal yesterdayRevenue = jdbc.queryForObject(
                "SELECT COALESCE(SUM(pay_amount), 0) FROM orders o WHERE status = 4 AND DATE(create_time) = DATE_SUB(CURDATE(), INTERVAL 1 DAY)" + sf,
                BigDecimal.class);
        if (yesterdayRevenue.compareTo(BigDecimal.ZERO) > 0) {
            double growth = todayRevenue.subtract(yesterdayRevenue)
                    .divide(yesterdayRevenue, 4, BigDecimal.ROUND_HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(1, BigDecimal.ROUND_HALF_UP).doubleValue();
            data.put("revenueGrowth", growth);
        }

        // 今日订单数
        Integer todayOrders = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders o WHERE DATE(create_time) = CURDATE()" + sf,
                Integer.class);
        data.put("todayOrders", todayOrders);

        // 昨日订单数（用于计算涨幅）
        Integer yesterdayOrders = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders o WHERE DATE(create_time) = DATE_SUB(CURDATE(), INTERVAL 1 DAY)" + sf,
                Integer.class);
        if (yesterdayOrders > 0) {
            int orderGrowth = todayOrders - yesterdayOrders;
            data.put("orderGrowth", orderGrowth);
        }

        // 总用户数（MERCHANT → 在自己店铺下过单的用户数）
        if (shopIds == null) {
            Integer totalUsers = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM `user` WHERE deleted = 0", Integer.class);
            data.put("totalUsers", totalUsers);
        } else if (shopIds.isEmpty()) {
            data.put("totalUsers", 0); // MERCHANT 无店铺 → 0
        } else {
            String shopIdsStr = shopIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            Integer totalUsers = jdbc.queryForObject(
                    "SELECT COUNT(DISTINCT o.user_id) FROM orders o WHERE o.shop_id IN (" + shopIdsStr + ")",
                    Integer.class);
            data.put("totalUsers", totalUsers);
        }

        // 在售商品（MERCHANT → 自己店铺的商品）
        if (shopIds == null) {
            Integer activeProducts = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM product WHERE status = 1 AND deleted = 0", Integer.class);
            data.put("activeProducts", activeProducts);
        } else if (shopIds.isEmpty()) {
            data.put("activeProducts", 0); // MERCHANT 无店铺 → 0
        } else {
            String shopIdsStr = shopIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            Integer activeProducts = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM product WHERE status = 1 AND deleted = 0 AND shop_id IN (" + shopIdsStr + ")",
                    Integer.class);
            data.put("activeProducts", activeProducts);
        }

        return Result.success(data);
    }

    @ApiOperation("销量&订单趋势（最近 N 天，MERCHANT 按店铺隔离）")
    @RequireRole({"ADMIN", "MERCHANT"})
    @GetMapping("/sales")
    public Result<Map<String, Object>> salesTrend(
            @ApiParam("天数") @RequestParam(defaultValue = "7") int days) {
        List<Long> shopIds = getMerchantShopIds();
        String sf = shopFilterForZeroable(shopIds, "o");

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT DATE(o.create_time) AS d, " +
                "  COUNT(*) AS order_count, " +
                "  COALESCE(SUM(o.pay_amount), 0) AS revenue " +
                "FROM orders o " +
                "WHERE DATE(o.create_time) >= DATE_SUB(CURDATE(), INTERVAL ? DAY)" + sf +
                " GROUP BY DATE(o.create_time) ORDER BY d",
                days);

        // 补全无数据的日期
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            map.put(r.get("d").toString(), r);
        }
        List<String> dates = new ArrayList<>();
        List<Integer> orderCounts = new ArrayList<>();
        List<BigDecimal> revenues = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            String dateStr = today.minusDays(i).toString();
            dates.add(dateStr);
            Map<String, Object> row = map.get(dateStr);
            orderCounts.add(row != null ? ((Number) row.get("order_count")).intValue() : 0);
            revenues.add(row != null ? new BigDecimal(row.get("revenue").toString()) : BigDecimal.ZERO);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dates", dates);
        result.put("orderCounts", orderCounts);
        result.put("revenues", revenues);
        return Result.success(result);
    }

    @ApiOperation("订单状态分布（MERCHANT 按店铺隔离）")
    @RequireRole({"ADMIN", "MERCHANT"})
    @GetMapping("/order-status")
    public Result<List<Map<String, Object>>> orderStatus() {
        List<Long> shopIds = getMerchantShopIds();
        String sf = shopFilterForZeroable(shopIds, "o");

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT o.status, COUNT(*) AS count FROM orders o WHERE 1=1" + sf + " GROUP BY o.status ORDER BY o.status");

        // 状态中文映射
        Map<Integer, String> labels = new HashMap<>();
        labels.put(0, "待支付"); labels.put(1, "待发货"); labels.put(2, "待收货");
        labels.put(3, "待评价"); labels.put(4, "已完成"); labels.put(-1, "已取消");
        labels.put(-2, "退款申请"); labels.put(-3, "已退款"); labels.put(-4, "管理员退款");

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            int status = ((Number) r.get("status")).intValue();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("status", status);
            item.put("label", labels.getOrDefault(status, "其他"));
            item.put("count", r.get("count"));
            result.add(item);
        }
        return Result.success(result);
    }

    @ApiOperation("热销商品 TopN（MERCHANT 按店铺隔离）")
    @RequireRole({"ADMIN", "MERCHANT"})
    @GetMapping("/product-sales")
    public Result<List<Map<String, Object>>> productSales(
            @ApiParam("返回条数") @RequestParam(defaultValue = "10") int limit) {
        List<Long> shopIds = getMerchantShopIds();

        String whereClause;
        if (shopIds == null) {
            whereClause = ""; // ADMIN → 全局
        } else if (shopIds.isEmpty()) {
            whereClause = "WHERE 1=0"; // MERCHANT 无店铺 → 无结果
        } else {
            String ids = shopIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            whereClause = "WHERE p.shop_id IN (" + ids + ")";
        }

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT oi.product_id, p.name AS product_name, p.main_image, " +
                "  SUM(oi.quantity) AS total_sales, SUM(oi.real_pay_amount) AS total_revenue " +
                "FROM order_item oi " +
                "JOIN product p ON p.id = oi.product_id " +
                whereClause +
                " GROUP BY oi.product_id, p.name, p.main_image " +
                "ORDER BY total_sales DESC LIMIT ?",
                limit);
        return Result.success(rows);
    }

    @ApiOperation("会员等级分布（MERCHANT 按自己店铺过滤）")
    @RequireRole({"ADMIN", "MERCHANT"})
    @GetMapping("/member-level")
    public Result<List<Map<String, Object>>> memberLevel() {
        List<Long> shopIds = getMerchantShopIds();

        String userFilter;
        if (shopIds == null) {
            userFilter = ""; // ADMIN → 全局
        } else if (shopIds.isEmpty()) {
            userFilter = " AND 1=0"; // MERCHANT 无店铺 → 无结果
        } else {
            String shopIdsStr = shopIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            userFilter = " AND u.id IN (SELECT DISTINCT o.user_id FROM orders o WHERE o.shop_id IN (" + shopIdsStr + "))";
        }

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT COALESCE(u.member_level_id, 0) AS level_id, " +
                "  CASE WHEN u.member_level_id IS NULL OR u.member_level_id = 0 THEN '非会员' " +
                "       ELSE COALESCE(ml.name, CONCAT('等级', CAST(u.member_level_id AS CHAR))) " +
                "  END AS level_name, " +
                "  COUNT(*) AS count " +
                "FROM `user` u " +
                "LEFT JOIN membership_level ml ON ml.id = u.member_level_id " +
                "WHERE u.deleted = 0" + userFilter + " " +
                "GROUP BY u.member_level_id ORDER BY level_id");
        return Result.success(rows);
    }
}
