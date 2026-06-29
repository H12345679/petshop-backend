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

    @ApiOperation("用户申请退单（2/3→-2）")
    @RequireLogin
    @PostMapping
    public Result<Map<String, Object>> apply(@RequestBody Map<String, Object> body) {
        Long orderId = toLong(body.get("orderId"));
        BigDecimal amount = body.get("amount") != null
                ? new BigDecimal(body.get("amount").toString()) : null;
        String reason = (String) body.get("reason");
        return Result.success(refundService.applyRefund(orderId, amount, reason));
    }

    @ApiOperation("后台审核退单（ADMIN·MERCHANT，-2→-3或驳回）")
    @RequireRole({"ADMIN", "MERCHANT"})
    @PutMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Integer status = body.get("status") != null
                ? Integer.valueOf(body.get("status").toString()) : null;
        String remark = (String) body.get("auditRemark");
        refundService.auditRefund(id, status, remark);
        return Result.success();
    }

    @ApiOperation("管理员直接退单（ADMIN·MERCHANT，3→-4）")
    @RequireRole({"ADMIN", "MERCHANT"})
    @PostMapping("/direct")
    public Result<Void> directRefund(@RequestBody Map<String, Object> body) {
        Long orderId = toLong(body.get("orderId"));
        String reason = (String) body.get("reason");
        refundService.directRefund(orderId, reason);
        return Result.success();
    }

    @ApiOperation("后台退单列表分页（ADMIN·MERCHANT）")
    @RequireRole({"ADMIN", "MERCHANT"})
    @GetMapping("/manage")
    public Result<PageResult<Refund>> manage(
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
