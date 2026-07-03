package com.petshop.order.controller;

import com.petshop.common.PageResult;
import com.petshop.common.Result;
import com.petshop.order.entity.Refund;
import com.petshop.order.service.RefundService;
import com.petshop.security.RequireLogin;
import com.petshop.security.RequireRole;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 退单接口（对应《项目接口设计文档》C 模块第 18~20 节）。
 */
@Api(tags = "07-退单")
@RestController
@RequestMapping("/api/refunds")
public class RefundController {

    @Autowired
    private RefundService refundService;

    @ApiOperation("用户申请退单（2/3/4→-2，支持仅退款/退货退款、是否收到货）")
    @RequireLogin
    @PostMapping
    public Result<Map<String, Object>> apply(@RequestBody Map<String, Object> body) {
        Long orderId = toLong(body.get("orderId"));
        BigDecimal amount = body.get("amount") != null
                ? new BigDecimal(body.get("amount").toString()) : null;
        String reason = (String) body.get("reason");
        // 1仅退款 2退货退款
        Integer refundType = body.get("refundType") != null
                ? Integer.valueOf(body.get("refundType").toString()) : null;
        // 0未收到货(快递退款) 1已收到货
        Integer received = body.get("received") != null
                ? Integer.valueOf(body.get("received").toString()) : null;
        String description = (String) body.get("description");
        List<String> images = new java.util.ArrayList<>();
        if (body.get("images") instanceof List) {
            for (Object o : (List<?>) body.get("images")) {
                if (o != null) images.add(o.toString());
            }
        }
        return Result.success(refundService.applyRefund(orderId, amount, reason,
                refundType, received, description, images));
    }

    @ApiOperation("后台审核退单（ADMIN·MERCHANT：仅退款通过→打款-3；退货退款通过→待用户退货；驳回→恢复原状态）")
    @RequireRole({"ADMIN", "MERCHANT"})
    @PutMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Integer status = body.get("status") != null
                ? Integer.valueOf(body.get("status").toString()) : null;
        String remark = (String) body.get("auditRemark");
        refundService.auditRefund(id, status, remark);
        return Result.success();
    }

    @ApiOperation("用户填写退货快递单号（退单状态3→4，等待商家确认收货）")
    @RequireLogin
    @PutMapping("/{id}/return-shipping")
    public Result<Void> returnShipping(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String courierCompany = (String) body.get("courierCompany");
        String trackingNumber = (String) body.get("trackingNumber");
        refundService.submitReturnShipping(id, courierCompany, trackingNumber);
        return Result.success();
    }

    @ApiOperation("商家确认收到退货并打款（ADMIN·MERCHANT，退单状态4→1，订单-2→-3）")
    @RequireRole({"ADMIN", "MERCHANT"})
    @PutMapping("/{id}/confirm-return")
    public Result<Void> confirmReturn(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String remark = body != null ? (String) body.get("remark") : null;
        refundService.confirmReturnReceived(id, remark);
        return Result.success();
    }

    @ApiOperation("管理员直接退单（ADMIN·MERCHANT，3→-4）")
    @RequireRole("ADMIN")
    @PostMapping("/direct")
    public Result<Void> directRefund(@RequestBody Map<String, Object> body) {
        // 支持 orderId 或 orderNo
        Long orderId = toLong(body.get("orderId"));
        String orderNo = (String) body.get("orderNo");
        String reason = (String) body.get("reason");
        refundService.directRefundByOrderIdOrNo(orderId, orderNo, reason);
        return Result.success();
    }

    @ApiOperation("后台退单列表分页（ADMIN·MERCHANT）")
    @RequireRole({"ADMIN", "MERCHANT"})
    @GetMapping("/manage")
    public Result<PageResult<Map<String, Object>>> manage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) Integer status) {
        return Result.success(refundService.managePage(current, size, shopId, status));
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        return Long.parseLong(v.toString());
    }
}
