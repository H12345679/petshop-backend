package com.petshop.order.service;

import com.petshop.common.PageResult;
import com.petshop.order.entity.Refund;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 退单服务接口（对应《项目接口设计文档》C 模块第 18~20 节）。
 */
public interface RefundService {

    /**
     * 用户申请退单（订单状态 2/3→-2，填理由）。
     * @return 退单信息 {refundNo}
     */
    Map<String, Object> applyRefund(Long orderId, BigDecimal amount, String reason);

    /**
     * 管理员审核退单（-2→-3 通过 / 驳回恢复原状态）。
     */
    void auditRefund(Long refundId, Integer status, String auditRemark);

    /**
     * 管理员直接退单（通过 orderId 或 orderNo，3→-4，无需用户申请）。
     */
    void directRefundByOrderIdOrNo(Long orderId, String orderNo, String reason);

    /** @deprecated 使用 directRefundByOrderIdOrNo */
    void directRefund(Long orderId, String reason);

    /** 后台退单列表分页（ADMIN·MERCHANT，返回含订单/商品/用户信息的Map） */
    PageResult<Map<String, Object>> managePage(int current, int size, Long shopId, Integer status);
}
