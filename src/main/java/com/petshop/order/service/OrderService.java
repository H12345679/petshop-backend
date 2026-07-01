package com.petshop.order.service;

import com.petshop.common.PageResult;

import java.util.List;
import java.util.Map;

/**
 * 订单服务接口（对应《项目接口设计文档》C 模块第 10~17 节）。
 */
public interface OrderService {

    /** 结算预览：传入购买项 + 优惠券，试算金额（不创建订单）。第一阶段 memberDiscount 固定为 0.00。 */
    Map<String, Object> preSettle(List<Map<String, Object>> items, Long userCouponId, Long addressId);

    /**
     * 创建订单（跨店自动拆单 + 库存预扣 + 幂等防重 + 优惠分摊 + CAS 锁券）。
     * @return {orderIds, orderNos, totalPayAmount}
     */
    Map<String, Object> createOrder(String requestId, Long userCouponId, Long addressId,
                                    List<Map<String, Object>> items, String remark);

    /** 模拟支付（余额扣款 + 订单状态/余额 CAS 防重复扣款）。仅状态 0→1。 */
    void pay(Long orderId, Integer payType);

    /** 批量支付（合并支付多个订单，一次性扣除总金额）。仅状态 0→1。 */
    void batchPay(List<Long> orderIds, Integer payType);

    /** 取消订单（仅 0/1→-1，恢复库存/优惠券/余额）。 */
    void cancel(Long orderId, String reason);

    /** 商家发货（ADMIN/MERCHANT，1→2）。 */
    void ship(Long orderId, String courierCompany, String trackingNumber);

    /** 用户确认收货（2→3）。 */
    void receive(Long orderId);

    /** 我的订单列表（分页）。 */
    PageResult<Map<String, Object>> myOrders(int current, int size, Integer status);

    /** 根据 ID 获取我的订单详情（含 orderItems）。 */
    Map<String, Object> getOrderById(Long orderId, Long userId);

    /** 后台订单管理分页（ADMIN·MERCHANT）。 */
    PageResult<Map<String, Object>> manageOrders(int current, int size, Long shopId,
                                                  String orderNo, Integer status);

    /** 删除订单（仅已取消/已完成/已退款等终态订单可删，物理删除 order_items 后逻辑删 order）。 */
    void deleteOrder(Long orderId);

    /**
     * 取消超时未支付订单：把创建时间早于 now-timeoutMinutes 且仍待支付(0)的订单
     * 自动置为已取消(-1)，并回滚库存与优惠券。由定时任务调用。
     * @return 本次取消的订单数
     */
    int cancelTimeoutOrders(int timeoutMinutes);
}
