package com.petshop.stats.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petshop.order.entity.Order;
import com.petshop.order.mapper.OrderMapper;
import com.petshop.product.entity.Product;
import com.petshop.product.mapper.ProductMapper;
import com.petshop.stats.service.StatsService;
import com.petshop.user.entity.MembershipLevel;
import com.petshop.user.entity.User;
import com.petshop.user.mapper.MembershipLevelMapper;
import com.petshop.user.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StatsServiceImpl implements StatsService {

    private static final String COUNT = "count";
    private static final String TOTAL_SALES = "totalSales";

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private MembershipLevelMapper membershipLevelMapper;

    @Autowired
    private com.petshop.shop.mapper.ShopMapper shopMapper;

    @Autowired
    private com.petshop.security.OwnershipChecker ownershipChecker;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Override
    public Map<String, Object> getKpi() {
        // 1) 鉴权与身份识别：判断当前是超级管理员还是普通商家。
        // 如果是商家，只能看到自己名下店铺的数据（通过 ownershipChecker 获取 shopIds）。
        List<Long> shopIds = ownershipChecker.myShopIds();
        Map<String, Object> data = new HashMap<>();

        // 如果是商家，但是他名下一家店都没有，那就直接返回一堆 0 鸭蛋，没必要查数据库了。
        if (shopIds != null && shopIds.isEmpty()) {
            data.put("todayRevenue", BigDecimal.ZERO);
            data.put("todayOrders", 0);
            data.put("totalUsers", 0L);
            data.put("activeProducts", 0L);
            return data;
        }

        // 2) 统计【今日营收】和【今日有效订单数】
        // 抓取今天零点以后的所有非取消状态（>=0）的有效订单
        LocalDateTime todayStart = LocalDate.now(ZoneId.systemDefault()).atStartOfDay();
        LambdaQueryWrapper<Order> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.ge(Order::getCreateTime, todayStart).ge(Order::getStatus, 0);
        if (shopIds != null) {
            orderWrapper.in(Order::getShopId, shopIds);
        }
        List<Order> todayOrdersList = orderMapper.selectList(orderWrapper);

        // 利用 Stream 流把每一单的实付金额累加起来，这就是今日总营收
        BigDecimal todayRevenue = todayOrdersList.stream()
                .map(o -> getEffectiveAmount(o))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int todayOrders = todayOrdersList.size();

        Long totalUsers = computeTotalUsers(shopIds);

        Long activeProducts = computeActiveProducts(shopIds);

        data.put("todayRevenue", todayRevenue);
        data.put("todayOrders", todayOrders);
        data.put("totalUsers", totalUsers);
        data.put("activeProducts", activeProducts);
        return data;
    }

    @Override
    public Map<String, Object> getSalesTrend(Integer days) {
        // 1) 默认查询最近 7 天的走势
        if (days == null || days <= 0) days = 7;
        List<String> dates = new ArrayList<>();
        List<Integer> orderCounts = new ArrayList<>();
        List<BigDecimal> revenues = new ArrayList<>();

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("MM-dd");
        LocalDate now = LocalDate.now(ZoneId.systemDefault());

        // 2) 身份隔离：没店铺的商家直接返回一堆 0，不要走下面的复杂逻辑
        List<Long> shopIds = ownershipChecker.myShopIds();
        if (shopIds != null && shopIds.isEmpty()) {
            for (int i = days - 1; i >= 0; i--) {
                dates.add(now.minusDays(i).format(dtf));
                orderCounts.add(0);
                revenues.add(BigDecimal.ZERO);
            }
            Map<String, Object> res = new HashMap<>();
            res.put("dates", dates);
            res.put("orderCounts", orderCounts);
            res.put("revenues", revenues);
            return res;
        }

        // 3) 高效查询：去订单表里一次性把这几天内的有效订单全部捞出来，然后在内存里按天做归类累加。
        // 这样比写复杂的 SQL GROUP BY 更好维护，且能兼容不同的数据库方言。
        LocalDateTime startTime = now.minusDays((long) days - 1).atStartOfDay();
        LambdaQueryWrapper<Order> query = new LambdaQueryWrapper<>();
        query.ge(Order::getCreateTime, startTime).ge(Order::getStatus, 0);
        if (shopIds != null) {
            query.in(Order::getShopId, shopIds);
        }
        List<Order> allOrders = orderMapper.selectList(query);

        for (int i = days - 1; i >= 0; i--) {
            LocalDate d = now.minusDays(i);
            dates.add(d.format(dtf));
            long count = allOrders.stream()
                    .filter(o -> o.getCreateTime() != null && o.getCreateTime().toLocalDate().equals(d))
                    .count();
            BigDecimal rev = allOrders.stream()
                    .filter(o -> o.getCreateTime() != null && o.getCreateTime().toLocalDate().equals(d))
                    .map(this::getEffectiveAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            orderCounts.add((int) count);
            revenues.add(rev);
        }

        Map<String, Object> res = new HashMap<>();
        res.put("dates", dates);
        res.put("orderCounts", orderCounts);
        res.put("revenues", revenues);
        return res;
    }

    @Override
    public List<Map<String, Object>> getOrderStatus() {
        List<Long> shopIds = ownershipChecker.myShopIds();
        if (shopIds != null && shopIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 一次性捞出所有订单，然后在内存中用 Stream 流的高级特性（groupingBy）按状态分组统计，
        // 这样可以避免对数据库进行高频词的 COUNT 分组查询，大大提高数据看板的响应速度。
        LambdaQueryWrapper<Order> query = new LambdaQueryWrapper<>();
        if (shopIds != null) {
            query.in(Order::getShopId, shopIds);
        }
        List<Order> allOrders = orderMapper.selectList(query);

        Map<Integer, Long> countMap = allOrders.stream()
                .filter(o -> o.getStatus() != null)
                .collect(Collectors.groupingBy(Order::getStatus, Collectors.counting()));

        Map<Integer, String> labelMap = new LinkedHashMap<>();
        labelMap.put(0, "待支付");
        labelMap.put(1, "待发货");
        labelMap.put(2, "待收货");
        labelMap.put(3, "待评价");
        labelMap.put(4, "已完成");
        labelMap.put(-1, "已取消");
        labelMap.put(-2, "退款申请");
        labelMap.put(-3, "已退款");
        labelMap.put(-4, "管理员退款");

        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : labelMap.entrySet()) {
            Long c = countMap.getOrDefault(entry.getKey(), 0L);
            if (c > 0 || (entry.getKey() >= 0 && entry.getKey() <= 4)) {
                Map<String, Object> item = new HashMap<>();
                item.put("label", entry.getValue());
                item.put(COUNT, c);
                list.add(item);
            }
        }
        return list;
    }

    @Override
    public List<Map<String, Object>> getMemberLevel() {
        List<Long> shopIds = ownershipChecker.myShopIds();
        if (shopIds != null && shopIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<User> users = fetchUsersForStats(shopIds);

        List<MembershipLevel> levels = membershipLevelMapper.selectList(null);
        Map<Long, String> levelNameMap = levels.stream().collect(Collectors.toMap(MembershipLevel::getId, MembershipLevel::getName));

        Map<String, Long> countByLevelName = new LinkedHashMap<>();
        countByLevelName.put("非会员", 0L);
        for (MembershipLevel l : levels) {
            countByLevelName.put(l.getName(), 0L);
        }

        populateLevelCounts(users, levelNameMap, countByLevelName, shopIds);

        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, Long> entry : countByLevelName.entrySet()) {
            if (entry.getValue() > 0 || list.size() < 4) {
                Map<String, Object> item = new HashMap<>();
                item.put("level_name", entry.getKey());
                item.put(COUNT, entry.getValue());
                list.add(item);
            }
        }
        return list;
    }

    @Override
    public List<Map<String, Object>> getProductSales(Integer limit) {
        if (limit == null || limit <= 0) limit = 10;
        List<Long> shopIds = ownershipChecker.myShopIds();
        if (shopIds != null && shopIds.isEmpty()) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<Product> query = new LambdaQueryWrapper<>();
        query.eq(Product::getStatus, 1);
        if (shopIds != null) {
            query.in(Product::getShopId, shopIds);
        }
        query.orderByDesc(Product::getSales);
        List<Product> products = productMapper.selectList(query);

        return buildRealProductSales(products, limit);
    }

    @Override
    public List<Map<String, Object>> getDailyStats(Integer days) {
        if (days == null || days <= 0) days = 7;
        LocalDate now = LocalDate.now(ZoneId.systemDefault());
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("MM-dd");
        java.sql.Timestamp since = java.sql.Timestamp.valueOf(now.minusDays((long) days - 1).atStartOfDay());

        Map<String, BigDecimal> revenueMap = new HashMap<>();
        List<Map<String, Object>> revRows = jdbcTemplate.queryForList(
                "SELECT DATE(create_time) AS d, SUM(COALESCE(pay_amount, total_amount, 0)) AS rev FROM orders WHERE create_time >= ? AND status >= 0 GROUP BY DATE(create_time)", since);
        for (Map<String, Object> r : revRows) {
            revenueMap.put(String.valueOf(r.get("d")), new BigDecimal(String.valueOf(r.get("rev"))));
        }

        Map<String, Long> userMap = new HashMap<>();
        List<Map<String, Object>> userRows = jdbcTemplate.queryForList(
                "SELECT DATE(create_time) AS d, COUNT(*) AS cnt FROM user WHERE create_time >= ? GROUP BY DATE(create_time)", since);
        for (Map<String, Object> r : userRows) {
            userMap.put(String.valueOf(r.get("d")), ((Number) r.get("cnt")).longValue());
        }

        Map<String, Long> opsMap = new HashMap<>();
        List<Map<String, Object>> opsRows = jdbcTemplate.queryForList(
                "SELECT DATE(create_time) AS d, COUNT(*) AS cnt FROM sys_log WHERE create_time >= ? GROUP BY DATE(create_time)", since);
        for (Map<String, Object> r : opsRows) {
            opsMap.put(String.valueOf(r.get("d")), ((Number) r.get("cnt")).longValue());
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate d = now.minusDays(i);
            String key = d.toString();
            Map<String, Object> item = new HashMap<>();
            item.put("date", d.format(dtf));
            item.put("revenue", revenueMap.getOrDefault(key, BigDecimal.ZERO));
            item.put("newUsers", userMap.getOrDefault(key, 0L));
            item.put("operationCount", opsMap.getOrDefault(key, 0L));
            list.add(item);
        }
        return list;
    }

    @Override
    public Map<String, Object> getLogOps(Integer days) {
        if (days == null || days <= 0) days = 7;
        java.sql.Timestamp since = java.sql.Timestamp.valueOf(
                LocalDate.now(ZoneId.systemDefault()).minusDays(days).atStartOfDay());
        Map<String, Object> result = new HashMap<>();

        // Top 10 高频操作（从 sys_log 真实统计）
        List<Map<String, Object>> topOps = jdbcTemplate.queryForList(
                "SELECT operation, COUNT(*) AS count FROM sys_log WHERE create_time >= ? AND operation IS NOT NULL AND operation != '' GROUP BY operation ORDER BY count DESC LIMIT 10",
                since);
        result.put("topOperations", topOps);

        // 24 小时活跃度分布（从 sys_log 真实统计）
        Map<Integer, Long> hourCountMap = new HashMap<>();
        List<Map<String, Object>> hourRows = jdbcTemplate.queryForList(
                "SELECT HOUR(create_time) AS h, COUNT(*) AS cnt FROM sys_log WHERE create_time >= ? GROUP BY HOUR(create_time)",
                since);
        for (Map<String, Object> r : hourRows) {
            hourCountMap.put(((Number) r.get("h")).intValue(), ((Number) r.get("cnt")).longValue());
        }
        List<Map<String, Object>> hourly = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            Map<String, Object> item = new HashMap<>();
            item.put("hour", h);
            item.put(COUNT, hourCountMap.getOrDefault(h, 0L));
            hourly.add(item);
        }
        result.put("hourlyDistribution", hourly);
        return result;
    }

    @Override
    public List<Map<String, Object>> getShopRanking(Integer limit) {
        if (limit == null || limit <= 0) limit = 10;
        List<Long> shopIds = ownershipChecker.myShopIds();
        if (shopIds != null && shopIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 查所有店铺，按销售额排序
        List<com.petshop.shop.entity.Shop> allShops = shopMapper.selectList(null);
        if (allShops.isEmpty()) {
            return new ArrayList<>();
        }

        // 按店铺聚合订单销售额
        Map<Long, BigDecimal> shopRevenue = new HashMap<>();
        Map<Long, Integer> shopOrderCount = new HashMap<>();
        aggregateOrdersByShop(allShops, shopIds, shopRevenue, shopOrderCount);

        List<Map<String, Object>> list = buildShopRankingList(allShops, shopRevenue, shopOrderCount, shopIds);

        list.sort((a, b) -> ((BigDecimal) b.get(TOTAL_SALES)).compareTo((BigDecimal) a.get(TOTAL_SALES)));
        if (list.size() > limit) list = list.subList(0, limit);

        return list;
    }

    private BigDecimal getEffectiveAmount(Order o) {
        if (o.getPayAmount() != null) {
            return o.getPayAmount();
        }
        if (o.getTotalAmount() != null) {
            return o.getTotalAmount();
        }
        return BigDecimal.ZERO;
    }

    private Long computeTotalUsers(List<Long> shopIds) {
        if (shopIds == null) {
            Long totalUsers = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getStatus, 1));
            return totalUsers != null ? totalUsers : 0L;
        }
        List<Order> merchantOrders = orderMapper.selectList(new LambdaQueryWrapper<Order>().in(Order::getShopId, shopIds));
        return merchantOrders.stream().map(Order::getUserId).filter(Objects::nonNull).distinct().count();
    }

    private Long computeActiveProducts(List<Long> shopIds) {
        LambdaQueryWrapper<Product> query = new LambdaQueryWrapper<Product>().eq(Product::getStatus, 1);
        if (shopIds != null) {
            query.in(Product::getShopId, shopIds);
        }
        Long activeProducts = productMapper.selectCount(query);
        return activeProducts != null ? activeProducts : 0L;
    }

    private List<User> fetchUsersForStats(List<Long> shopIds) {
        if (shopIds != null) {
            List<Order> merchantOrders = orderMapper.selectList(new LambdaQueryWrapper<Order>().in(Order::getShopId, shopIds));
            Set<Long> userIds = merchantOrders.stream().map(Order::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
            if (userIds.isEmpty()) {
                return new ArrayList<>();
            }
            return userMapper.selectBatchIds(userIds);
        }
        return userMapper.selectList(null);
    }

    private void populateLevelCounts(List<User> users, Map<Long, String> levelNameMap,
                                     Map<String, Long> countByLevelName, List<Long> shopIds) {
        for (User u : users) {
            Long lid = u.getMemberLevelId();
            if (lid == null || lid <= 0 || !levelNameMap.containsKey(lid)) {
                countByLevelName.put("非会员", countByLevelName.get("非会员") + 1);
            } else {
                String name = levelNameMap.get(lid);
                countByLevelName.put(name, countByLevelName.getOrDefault(name, 0L) + 1);
            }
        }
    }

    private List<Map<String, Object>> buildRealProductSales(List<Product> products, int limit) {
        List<Map<String, Object>> list = new ArrayList<>();
        int count = 0;
        for (Product p : products) {
            if (count >= limit) break;
            Map<String, Object> item = new HashMap<>();
            item.put("product_name", p.getName() != null ? p.getName() : "商品" + p.getId());
            item.put("total_sales", p.getSales() != null ? p.getSales() : 0);
            list.add(item);
            count++;
        }
        return list;
    }

    private void aggregateOrdersByShop(List<com.petshop.shop.entity.Shop> allShops, List<Long> shopIds,
                                       Map<Long, BigDecimal> shopRevenue, Map<Long, Integer> shopOrderCount) {
        for (com.petshop.shop.entity.Shop s : allShops) {
            shopRevenue.put(s.getId(), BigDecimal.ZERO);
            shopOrderCount.put(s.getId(), 0);
        }
        LambdaQueryWrapper<Order> query = new LambdaQueryWrapper<>();
        query.ge(Order::getStatus, 1);
        if (shopIds != null) {
            query.in(Order::getShopId, shopIds);
        }
        List<Order> orders = orderMapper.selectList(query);
        for (Order o : orders) {
            if (o.getShopId() == null) continue;
            BigDecimal amt = getEffectiveAmount(o);
            shopRevenue.merge(o.getShopId(), amt, BigDecimal::add);
            shopOrderCount.merge(o.getShopId(), 1, Integer::sum);
        }
    }

    private List<Map<String, Object>> buildShopRankingList(List<com.petshop.shop.entity.Shop> allShops,
                                                           Map<Long, BigDecimal> shopRevenue,
                                                           Map<Long, Integer> shopOrderCount,
                                                           List<Long> shopIds) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (com.petshop.shop.entity.Shop s : allShops) {
            BigDecimal rev = shopRevenue.getOrDefault(s.getId(), BigDecimal.ZERO);
            if (shopIds != null && !shopIds.contains(s.getId())) continue;
            Map<String, Object> item = new HashMap<>();
            item.put("shopName", s.getName() != null ? s.getName() : "店铺" + s.getId());
            item.put(TOTAL_SALES, rev);
            item.put("orderCount", shopOrderCount.getOrDefault(s.getId(), 0));
            list.add(item);
        }
        return list;
    }
}
