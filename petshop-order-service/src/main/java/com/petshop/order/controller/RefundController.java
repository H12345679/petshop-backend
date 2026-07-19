package com.petshop.order.controller;

import com.petshop.common.PageResult;
import com.petshop.common.Result;
import com.petshop.order.service.RefundService;
import com.petshop.log.annotation.LogOperation;
import com.petshop.security.RequireLogin;
import com.petshop.security.RequireRole;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 退单接口。
 */
@Tag(name = "07-退单")
@RestController
@RequestMapping("/api/refunds")
public class RefundController {

    @Autowired
    private RefundService refundService;

    @Operation(summary = "用户申请退单(2/3/4→-2,支持仅退款/退货退款、是否收到货)")
    @RequireLogin
    @LogOperation("申请退单")
    @PostMapping
    public Result<Map<String, Object>> apply(@RequestBody Map<String, Object> body) {
        Long orderId = toLong(body.get("orderId"));
        Long orderItemId = toLong(body.get("orderItemId"));
        BigDecimal amount = toBigDecimal(body.get("amount"));
        String reason = (String) body.get("reason");
        // 1仅退款 2退货退款
        Integer refundType = toInteger(body.get("refundType"));
        // 0未收到货(快递退款) 1已收到货
        Integer received = toInteger(body.get("received"));
        String description = (String) body.get("description");
        List<String> images = new java.util.ArrayList<>();
        if (body.get("images") instanceof List) {
            for (Object o : (List<?>) body.get("images")) {
                if (o != null) images.add(o.toString());
            }
        }
        return Result.success(refundService.applyRefund(orderId, orderItemId, amount, reason,
                refundType, received, description, images));
    }

    @Operation(summary = "后台审核退单(ADMIN·MERCHANT: 仅退款通过→打款-3; 退货退款通过→待用户退货; 驳回→恢复原状态)")
    @RequireRole({"ADMIN", "MERCHANT"})
    @LogOperation("审核退单")
    @PutMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Integer status = toInteger(body.get("status"));
        String remark = (String) body.get("auditRemark");
        refundService.auditRefund(id, status, remark);
        return Result.success();
    }

    @Operation(summary = "用户填写退货快递单号(退单状态3→4,等待商家确认收货)")
    @RequireLogin
    @LogOperation("填写退货快递单号")
    @PutMapping("/{id}/return-shipping")
    public Result<Void> returnShipping(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String courierCompany = (String) body.get("courierCompany");
        String trackingNumber = (String) body.get("trackingNumber");
        refundService.submitReturnShipping(id, courierCompany, trackingNumber);
        return Result.success();
    }

    @Operation(summary = "商家确认收到退货并打款(ADMIN·MERCHANT,退单状态4→1,订单-2→-3)")
    @RequireRole({"ADMIN", "MERCHANT"})
    @LogOperation("确认收货退款")
    @PutMapping("/{id}/confirm-return")
    public Result<Void> confirmReturn(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String remark = body != null ? (String) body.get("remark") : null;
        refundService.confirmReturnReceived(id, remark);
        return Result.success();
    }

    @Operation(summary = "管理员直接退单(ADMIN·MERCHANT,3→-4)")
    @RequireRole("ADMIN")
    @LogOperation("管理员直接退单")
    @PostMapping("/direct")
    public Result<Void> directRefund(@RequestBody Map<String, Object> body) {
        // 支持 orderId 或 orderNo
        Long orderId = toLong(body.get("orderId"));
        String orderNo = (String) body.get("orderNo");
        String reason = (String) body.get("reason");
        refundService.directRefundByOrderIdOrNo(orderId, orderNo, reason);
        return Result.success();
    }

    @Operation(summary = "后台退单列表分页(ADMIN·MERCHANT)")
    @RequireRole({"ADMIN", "MERCHANT"})
    @GetMapping("/manage")
    public Result<PageResult<Map<String, Object>>> manage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String refundNo,
            @RequestParam(required = false) String username) {
        return Result.success(refundService.managePage(current, size, shopId, status, refundNo, username));
    }

    // ==================== 私有辅助方法 ====================

    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer toInteger(Object v) {
        if (v == null) return null;
        if (v instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        try {
            return new BigDecimal(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
