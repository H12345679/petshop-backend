package com.petshop.order.service;

import com.petshop.common.PageResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 退单服务接口（对应《项目接口设计文档》C 模块第 18~20 节）。
 */
public interface RefundService {

    /**
     * 用户申请退单（订单状态 2/3/4→-2）。
     * <p>规则：
     * <ul>
     *   <li>待收货(2)可声明"未收到货"(received=0，快递退回/丢件)，此时只能仅退款；</li>
     *   <li>已收货(3)/已评价(4)默认已收到货；</li>
     *   <li>已评价(4)必须选择退货退款(refundType=2)。</li>
     * </ul>
     * @param refundType 1仅退款 2退货退款
     * @param received   0未收到货 1已收到货
     * @return 退单信息 {refundNo, refundId}
     */
    Map<String, Object> applyRefund(Long orderId, Long orderItemId, BigDecimal amount, String reason,
                                    Integer refundType, Integer received,
                                    String description, List<String> images);

    /**
     * 管理员/商家审核退单。
     * <ul>
     *   <li>仅退款通过：直接打款，订单 -2→-3；</li>
     *   <li>退货退款通过：退单进入"待用户退货"(3)，订单停留在 -2，等用户填退货单号；</li>
     *   <li>驳回：订单恢复原状态(2/3/4)。</li>
     * </ul>
     */
    void auditRefund(Long refundId, Integer status, String auditRemark);

    /**
     * 用户提交退货物流单号（退单状态 3→4，等待商家确认收货）。
     */
    void submitReturnShipping(Long refundId, String courierCompany, String trackingNumber);

    /**
     * 商家/管理员确认收到退货并打款（退单状态 4→1，订单 -2→-3）。
     */
    void confirmReturnReceived(Long refundId, String remark);

    /**
     * 管理员直接退单（通过 orderId 或 orderNo，3→-4，无需用户申请，需录入理由）。
     */
    void directRefundByOrderIdOrNo(Long orderId, String orderNo, String reason);

    /** @deprecated 使用 directRefundByOrderIdOrNo */
    @Deprecated
    void directRefund(Long orderId, String reason);

    /** 后台退单列表分页（ADMIN·MERCHANT，返回含订单/商品/用户信息的Map） */
    PageResult<Map<String, Object>> managePage(int current, int size, Long shopId, Integer status, String refundNo, String username);
}
