/**
 * 订单·购物车·退单·评价 模块 Service 层。
 * <p>
 * 由 C 负责实现，包含：
 * <ul>
 *   <li>CartItemService —— 购物车增删改查</li>
 *   <li>OrderService —— 下单、支付、发货、收货、取消（核心状态机）</li>
 *   <li>RefundService —— 退单申请 &amp; 审核</li>
 *   <li>ReviewService —— 评价</li>
 *   <li>CouponService —— 优惠券（加分项）</li>
 * </ul>
 *
 * 状态机流转请使用 {@link com.petshop.common.OrderStatus#checkTransition(int, int)}。
 *
 * 规范：
 * <pre>
 *   public interface OrderService extends IService&lt;Order&gt; { ... }
 *
 *   &#64;Service
 *   public class OrderServiceImpl extends ServiceImpl&lt;OrderMapper, Order&gt; implements OrderService { ... }
 * </pre>
 * 参考 {@link com.petshop.controller.demo.DemoItemService} 和 {@link com.petshop.controller.demo.DemoItemServiceImpl}。
 */
package com.petshop.order.service;
