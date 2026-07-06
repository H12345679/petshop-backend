package com.petshop.order.controller;

import com.petshop.common.PageResult;
import com.petshop.common.Result;
import com.petshop.order.service.OrderService;
import com.petshop.security.RequireLogin;
import com.petshop.security.RequireRole;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 订单接口。
 */
@Api(tags = "06-订单")
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    // ==================== 前台 ====================

    @ApiOperation("结算预览（金额试算，不创建订单）")
    @RequireLogin
    @PostMapping("/pre-settle")
    public Result<Map<String, Object>> preSettle(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        Long couponId = toLong(body.get("couponId"), 0L);
        Long addressId = toLong(body.get("addressId"), 0L);
        return Result.success(orderService.preSettle(items, couponId, addressId));
    }

    @ApiOperation("创建订单（跨店自动拆单 + 幂等防重）")
    @RequireLogin
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        String requestId = (String) body.get("requestId");
        Long couponId = toLong(body.get("couponId"), 0L);
        Long addressId = toLong(body.get("addressId"), 0L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        String remark = (String) body.get("remark");
        return Result.success(orderService.createOrder(requestId, couponId, addressId, items, remark));
    }

    @ApiOperation("我的订单列表（分页）")
    @RequireLogin
    @GetMapping("/my")
    public Result<PageResult<Map<String, Object>>> myOrders(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer status) {
        return Result.success(orderService.myOrders(current, size, status));
    }

    @ApiOperation("模拟支付（余额扣款，仅 0→1)")
    @RequireLogin
    @PutMapping("/{id}/pay")
    public Result<Void> pay(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Integer payType = toInteger(body.get("payType"), 1);
        orderService.pay(id, payType);
        return Result.success();
    }

    @ApiOperation("批量支付（合并支付多个订单）")
    @RequireLogin
    @PostMapping("/batch-pay")
    public Result<Void> batchPay(@RequestBody Map<String, Object> body) {
        // 订单id为雪花ID，前端以字符串回传，需逐个转 Long（直接强转 List<Long> 会因泛型擦除在遍历时抛 ClassCastException）
        Object rawIds = body.get("orderIds");
        List<Long> orderIds = new java.util.ArrayList<>();
        if (rawIds instanceof List) {
            for (Object o : (List<?>) rawIds) {
                if (o != null) orderIds.add(toLong(o, null));
            }
        }
        Integer payType = toInteger(body.get("payType"), 1);
        orderService.batchPay(orderIds, payType);
        return Result.success();
    }

    @ApiOperation("获取我的订单详情（含 orderItems)")
    @RequireLogin
    @GetMapping("/{id}")
    public Result<Map<String, Object>> getOrder(@PathVariable Long id) {
        Long userId = com.petshop.security.UserContext.getUserId();
        return Result.success(orderService.getOrderById(id, userId));
    }

    @ApiOperation("取消订单（仅 0/1→-1,回滚库存/优惠券/余额）")
    @RequireLogin
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String reason = body.get("cancelReason") != null ? body.get("cancelReason").toString() : "用户取消";
        orderService.cancel(id, reason);
        return Result.success();
    }

    @ApiOperation("商家发货(ADMIN/MERCHANT, 1→2)")
    @RequireRole({"ADMIN", "MERCHANT"})
    @PutMapping("/{id}/ship")
    public Result<Void> ship(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String courierCompany = body != null ? (String) body.get("courierCompany") : null;
        String trackingNumber = body != null ? (String) body.get("trackingNumber") : null;
        orderService.ship(id, courierCompany, trackingNumber);
        return Result.success();
    }

    @ApiOperation("用户确认收货(2→3)")
    @RequireLogin
    @PutMapping("/{id}/receive")
    public Result<Void> receive(@PathVariable Long id) {
        orderService.receive(id);
        return Result.success();
    }

    @ApiOperation("删除订单（仅终态：已取消/已完成/已退款）")
    @RequireLogin
    @DeleteMapping("/{id}")
    public Result<Void> deleteOrder(@PathVariable Long id) {
        orderService.deleteOrder(id);
        return Result.success();
    }

    // ==================== 后台 ====================

    @ApiOperation("后台订单管理列表(ADMIN·MERCHANT)")
    @RequireRole({"ADMIN", "MERCHANT"})
    @GetMapping("/manage")
    public Result<PageResult<Map<String, Object>>> manage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) Integer status) {
        return Result.success(orderService.manageOrders(current, size, shopId, orderNo, status));
    }

    // ==================== 私有辅助方法 ====================

    private Long toLong(Object v, Long defaultValue) {
        if (v == null) return defaultValue;
        if (v instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Integer toInteger(Object v, Integer defaultValue) {
        if (v == null) return defaultValue;
        if (v instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
