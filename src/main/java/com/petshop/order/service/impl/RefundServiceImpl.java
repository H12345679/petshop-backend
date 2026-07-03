package com.petshop.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.petshop.common.BusinessException;
import com.petshop.common.OrderStatus;
import com.petshop.common.PageResult;
import com.petshop.common.ResultCode;
import com.petshop.order.entity.*;
import com.petshop.order.mapper.*;
import com.petshop.order.service.RefundService;
import com.petshop.product.entity.Product;
import com.petshop.product.entity.ProductSku;
import com.petshop.product.mapper.ProductMapper;
import com.petshop.product.mapper.ProductSkuMapper;
import com.petshop.security.OwnershipChecker;
import com.petshop.security.UserContext;
import com.petshop.user.entity.User;
import com.petshop.user.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 退单 Service 实现。
 * <p>
 * 核心：申请退单（备份原状态→-2）、审核、直接退单（→-4）。退款金额上限为 order.pay_amount。
 * <p>
 * 两条退款路径：
 * <pre>
 *  仅退款(refund_type=1)：  申请(0) ──商家通过──> 打款(1)，订单 -2→-3
 *    · 待收货(2)未收到货(received=0)即"快递退款"，商家操作按钮为【确认退货退款】
 *  退货退款(refund_type=2)：申请(0) ──商家同意退货──> 待用户退货(3)
 *                          ──用户填退货快递单号──> 待商家确认收货(4)
 *                          ──商家确认收货──> 打款(1)，订单 -2→-3
 *    · 已评价(订单状态4)只能走此路径，商家审核界面有特别提示
 *  任一路径驳回(2)：订单恢复原状态(2/3/4)
 * </pre>
 */
@Service
public class RefundServiceImpl extends ServiceImpl<RefundMapper, Refund> implements RefundService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderItemMapper orderItemMapper;
    @Autowired
    private OrderStatusLogMapper orderStatusLogMapper;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private ProductSkuMapper productSkuMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private OwnershipChecker ownershipChecker;

    private static final String REFUND_NO_PREFIX = "RFD";

    @Override
    @Transactional
    public Map<String, Object> applyRefund(Long orderId, BigDecimal amount, String reason,
                                           Integer refundType, Integer received,
                                           String description, List<String> images) {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new BusinessException(ResultCode.UNAUTHORIZED);

        Order order = orderMapper.selectById(orderId);
        if (order == null) throw new BusinessException(ResultCode.NOT_FOUND);
        if (!userId.equals(order.getUserId())) throw new BusinessException(ResultCode.FORBIDDEN);

        int currentStatus = order.getStatus();
        if (currentStatus != 2 && currentStatus != 3 && currentStatus != 4) {
            throw new BusinessException("当前订单状态不可申请退款（" + statusDesc(currentStatus) + "）");
        }

        int type = (refundType != null && refundType == 2) ? 2 : 1;
        // 只有待收货(2)才可能"未收到货"；已收货(3)/已评价(4)一定收到了货
        int recv = (currentStatus == 2 && received != null && received == 0) ? 0 : 1;
        if (recv == 0) {
            // 未收到货（快递退回/丢件）没有货可退，只能仅退款
            type = 1;
        }
        if (currentStatus == 4 && type != 2) {
            throw new BusinessException("已评价的订单退款必须退货，请选择退货退款");
        }

        // 退款金额不能超过订单实付
        BigDecimal refundAmount = (amount != null && amount.compareTo(order.getPayAmount()) <= 0)
                ? amount : order.getPayAmount();

        Refund refund = new Refund();
        refund.setRefundNo(nextRefundNo());
        refund.setOrderId(orderId);
        refund.setUserId(userId);
        refund.setAmount(refundAmount);
        refund.setReason(reason);
        refund.setDescription(description);
        refund.setImages(toJsonArray(images));
        refund.setType(1);   // 用户申请
        refund.setRefundType(type);
        refund.setReceived(recv);
        refund.setStatus(0); // 申请中
        this.save(refund);

        // 备份原状态
        order.setPrevStatus(currentStatus);
        order.setStatus(-2);
        orderMapper.updateById(order);

        String logRemark = (type == 2 ? "[退货退款] " : (recv == 0 ? "[仅退款·未收到货] " : "[仅退款] ")) + reason;
        saveStatusLog(orderId, currentStatus, -2, userId, "USER", logRemark);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("refundNo", refund.getRefundNo());
        result.put("refundId", refund.getId());
        return result;
    }

    @Override
    @Transactional
    public void auditRefund(Long refundId, Integer status, String auditRemark) {
        Long operatorId = UserContext.getUserId();
        String role = UserContext.getRole();
        if (operatorId == null) throw new BusinessException(ResultCode.UNAUTHORIZED);

        Refund refund = this.getById(refundId);
        if (refund == null) throw new BusinessException(ResultCode.NOT_FOUND);
        if (refund.getStatus() == null || refund.getStatus() != 0) {
            throw new BusinessException("该退单已处理");
        }

        Order order = orderMapper.selectById(refund.getOrderId());
        if (order == null) throw new BusinessException("关联订单不存在");
        ownershipChecker.assertOrderOwned(order.getId());

        if (status != null && status == 1) {
            boolean needReturn = refund.getRefundType() != null && refund.getRefundType() == 2;

            if (needReturn) {
                // ===== 退货退款：同意退货，等用户寄回填单号，先不打款 =====
                refund.setStatus(3); // 待用户退货
                refund.setAuditUserId(operatorId);
                refund.setAuditTime(LocalDateTime.now());
                refund.setAuditRemark(auditRemark);
                this.updateById(refund);

                // 订单停留在 -2，仅记录流转日志
                saveStatusLog(order.getId(), -2, -2, operatorId, role,
                        "同意退货，待用户寄回并填写退货单号" + (auditRemark != null && !auditRemark.isEmpty() ? "：" + auditRemark : ""));
            } else {
                // ===== 仅退款：审核通过直接打款 → -3 =====
                int from = order.getStatus();
                OrderStatus.checkTransition(from, -3);

                BigDecimal refundAmount = refund.getAmount();
                if (refundAmount.compareTo(order.getPayAmount()) > 0) {
                    refundAmount = order.getPayAmount();
                }
                refundToBalance(order, refundAmount);

                refund.setStatus(1);
                refund.setAuditUserId(operatorId);
                refund.setAuditTime(LocalDateTime.now());
                refund.setAuditRemark(auditRemark);
                this.updateById(refund);

                order.setStatus(-3);
                orderMapper.updateById(order);

                saveStatusLog(order.getId(), from, -3, operatorId, role, auditRemark);
                // 退款成功后恢复库存
                rollbackStock(order.getId());
            }

        } else {
            // ===== 驳回 → 恢复原状态 =====
            int from = order.getStatus();
            Integer prev = order.getPrevStatus();
            if (prev == null || (prev != 2 && prev != 3 && prev != 4)) {
                throw new BusinessException("无法恢复原状态");
            }
            OrderStatus.checkTransition(from, prev);

            refund.setStatus(2); // 审核拒绝
            refund.setAuditUserId(operatorId);
            refund.setAuditTime(LocalDateTime.now());
            refund.setAuditRemark(auditRemark);
            this.updateById(refund);

            order.setStatus(prev);
            order.setPrevStatus(null);
            orderMapper.updateById(order);

            saveStatusLog(order.getId(), from, prev, operatorId, role, "驳回：" + auditRemark);
        }
    }

    @Override
    @Transactional
    public void submitReturnShipping(Long refundId, String courierCompany, String trackingNumber) {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new BusinessException(ResultCode.UNAUTHORIZED);
        if (trackingNumber == null || trackingNumber.trim().isEmpty()) {
            throw new BusinessException("请输入退货快递单号");
        }

        Refund refund = this.getById(refundId);
        if (refund == null) throw new BusinessException(ResultCode.NOT_FOUND);
        if (!userId.equals(refund.getUserId())) throw new BusinessException(ResultCode.FORBIDDEN);
        if (refund.getStatus() == null || refund.getStatus() != 3) {
            throw new BusinessException("当前退单不在待退货状态，无法填写退货单号");
        }

        refund.setReturnCourierCompany(courierCompany);
        refund.setReturnTrackingNumber(trackingNumber.trim());
        refund.setReturnTime(LocalDateTime.now());
        refund.setStatus(4); // 待商家确认收货
        this.updateById(refund);

        saveStatusLog(refund.getOrderId(), -2, -2, userId, "USER",
                "用户已寄回退货，快递：" + (courierCompany != null ? courierCompany : "") + " " + trackingNumber.trim());
    }

    @Override
    @Transactional
    public void confirmReturnReceived(Long refundId, String remark) {
        Long operatorId = UserContext.getUserId();
        String role = UserContext.getRole();
        if (operatorId == null) throw new BusinessException(ResultCode.UNAUTHORIZED);

        Refund refund = this.getById(refundId);
        if (refund == null) throw new BusinessException(ResultCode.NOT_FOUND);
        if (refund.getStatus() == null || refund.getStatus() != 4) {
            throw new BusinessException("退单不在待确认收货状态（需用户先填写退货单号）");
        }

        Order order = orderMapper.selectById(refund.getOrderId());
        if (order == null) throw new BusinessException("关联订单不存在");
        ownershipChecker.assertOrderOwned(order.getId());

        int from = order.getStatus();
        OrderStatus.checkTransition(from, -3);

        BigDecimal refundAmount = refund.getAmount();
        if (refundAmount.compareTo(order.getPayAmount()) > 0) {
            refundAmount = order.getPayAmount();
        }
        refundToBalance(order, refundAmount);

        refund.setStatus(1); // 已退款(结束)
        if (remark != null && !remark.trim().isEmpty()) {
            String old = refund.getAuditRemark();
            refund.setAuditRemark((old != null && !old.isEmpty() ? old + "；" : "") + "确认收货：" + remark.trim());
        }
        this.updateById(refund);

        order.setStatus(-3);
        orderMapper.updateById(order);

        saveStatusLog(order.getId(), from, -3, operatorId, role,
                "商家确认收到退货（单号 " + refund.getReturnTrackingNumber() + "），退款完成");
        rollbackStock(order.getId());
    }

    @Override
    @Transactional
    public void directRefund(Long orderId, String reason) {
        Long operatorId = UserContext.getUserId();
        String role = UserContext.getRole();
        if (operatorId == null) throw new BusinessException(ResultCode.UNAUTHORIZED);
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessException("请录入退单理由");
        }

        Order order = orderMapper.selectById(orderId);
        if (order == null) throw new BusinessException(ResultCode.NOT_FOUND);
        ownershipChecker.assertOrderOwned(order.getId());

        int currentStatus = order.getStatus();
        if (currentStatus != 3) {
            throw new BusinessException("已收货的订单才可直接退单（当前：" + statusDesc(currentStatus) + "）");
        }
        OrderStatus.checkTransition(currentStatus, -4);

        Refund refund = new Refund();
        refund.setRefundNo(nextRefundNo());
        refund.setOrderId(orderId);
        refund.setUserId(order.getUserId());
        refund.setAmount(order.getPayAmount());
        refund.setReason(reason);
        refund.setType(2);       // 管理员直接退
        refund.setRefundType(1); // 直接退款，不走退货流程
        refund.setReceived(1);
        refund.setStatus(1);     // 已退
        refund.setAuditUserId(operatorId);
        refund.setAuditTime(LocalDateTime.now());
        refund.setAuditRemark(reason);
        this.save(refund);

        // 退回余额；直接退款仅从已收货(3)发起，收货赠送的积分一并扣回
        order.setPrevStatus(currentStatus);
        refundToBalance(order, order.getPayAmount());

        order.setStatus(-4);
        orderMapper.updateById(order);

        saveStatusLog(orderId, currentStatus, -4, operatorId, role, reason);
        rollbackStock(orderId);
    }

    @Override
    @Transactional
    public void directRefundByOrderIdOrNo(Long orderId, String orderNo, String reason) {
        if (orderId == null && orderNo != null) {
            // 根据 orderNo 查 orderId
            LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Order::getOrderNo, orderNo);
            Order order = orderMapper.selectOne(wrapper);
            if (order == null) throw new BusinessException(ResultCode.NOT_FOUND);
            orderId = order.getId();
        }
        if (orderId == null) throw new BusinessException(400, "请提供订单号");
        this.directRefund(orderId, reason);
    }

    @Override
    public PageResult<Map<String, Object>> managePage(int current, int size, Long shopId, Integer status, String refundNo, String username) {
        LambdaQueryWrapper<Refund> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Refund::getStatus, status);
        }
        if (refundNo != null && !refundNo.isEmpty()) {
            wrapper.eq(Refund::getRefundNo, refundNo);
        }
        if (username != null && !username.isEmpty()) {
            List<User> users = userMapper.selectList(
                    new LambdaQueryWrapper<User>()
                            .like(User::getUsername, username)
                            .or()
                            .like(User::getNickname, username));
            if (users != null && !users.isEmpty()) {
                List<Long> uids = new ArrayList<>();
                for (User u : users) {
                    uids.add(u.getId());
                }
                wrapper.in(Refund::getUserId, uids);
            } else {
                wrapper.eq(Refund::getId, -1L);
            }
        }
        wrapper.orderByDesc(Refund::getCreateTime);
        Page<Refund> page = this.page(new Page<>(current, size), wrapper);

        List<Long> shopIds = ownershipChecker.myShopIds();
        List<Map<String, Object>> records = new ArrayList<>();
        long total = page.getTotal();

        for (Refund r : page.getRecords()) {
            // 查关联订单
            Order order = orderMapper.selectById(r.getOrderId());
            if (order == null) continue;
            // MERCHANT 按店铺过滤
            if (shopIds != null && !shopIds.contains(order.getShopId())) continue;

            Map<String, Object> vo = new LinkedHashMap<>();
            vo.put("id", r.getId());
            vo.put("refundNo", r.getRefundNo());
            vo.put("orderId", r.getOrderId());
            vo.put("orderNo", order.getOrderNo());
            vo.put("userId", r.getUserId());
            // 买家名
            User user = userMapper.selectById(r.getUserId());
            vo.put("buyerName", user != null ? (user.getNickname() != null ? user.getNickname() : user.getUsername()) : "用户#" + r.getUserId());
            vo.put("amount", r.getAmount());
            vo.put("reason", r.getReason());
            vo.put("description", r.getDescription());
            vo.put("images", r.getImages());
            vo.put("type", r.getType());
            vo.put("refundType", r.getRefundType());
            vo.put("received", r.getReceived());
            vo.put("status", r.getStatus());
            vo.put("auditRemark", r.getAuditRemark());
            vo.put("auditTime", r.getAuditTime());
            vo.put("returnCourierCompany", r.getReturnCourierCompany());
            vo.put("returnTrackingNumber", r.getReturnTrackingNumber());
            vo.put("returnTime", r.getReturnTime());
            // 已评价订单退款的特别提示（申请退款前订单处于 4-已评价）
            vo.put("reviewed", order.getPrevStatus() != null && order.getPrevStatus() == 4);
            vo.put("createTime", r.getCreateTime());

            // 查订单明细，计算可退上限 & 商品名
            LambdaQueryWrapper<OrderItem> oiWrapper = new LambdaQueryWrapper<>();
            oiWrapper.eq(OrderItem::getOrderId, order.getId());
            List<OrderItem> items = orderItemMapper.selectList(oiWrapper);
            BigDecimal maxRefund = BigDecimal.ZERO;
            String productName = "—";
            if (!items.isEmpty()) {
                OrderItem firstItem = items.get(0);
                productName = firstItem.getProductName() != null ? firstItem.getProductName() : "商品";
                if (items.size() > 1) productName += " 等" + items.size() + "件";
                for (OrderItem oi : items) {
                    BigDecimal rpa = oi.getRealPayAmount() != null ? oi.getRealPayAmount() : BigDecimal.ZERO;
                    maxRefund = maxRefund.add(rpa);
                }
            }
            vo.put("productName", productName);
            vo.put("maxRefund", maxRefund);

            records.add(vo);
        }

        PageResult<Map<String, Object>> pr = new PageResult<>();
        pr.setTotal(shopIds != null ? records.size() : total);
        pr.setPages(shopIds != null ? 1 : page.getPages());
        pr.setCurrent(current);
        pr.setSize(size);
        pr.setRecords(records);
        return pr;
    }

    // ========== 内部 ==========

    /** 生成退单号：RFD + yyyyMMdd + 毫秒后6位 */
    private String nextRefundNo() {
        return REFUND_NO_PREFIX + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + String.format("%06d", System.currentTimeMillis() % 1000000);
    }

    /** 凭证图片列表 → JSON 数组字符串 */
    private String toJsonArray(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        try {
            return JSON_MAPPER.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /**
     * 退款打回余额；若退款前订单已收货（原状态 3待评价 / 4已评价），
     * 扣回收货时赠送的积分，防止"收货→退款"反复刷积分。
     */
    private void refundToBalance(Order order, BigDecimal refundAmount) {
        User user = userMapper.selectById(order.getUserId());
        if (user == null) return;
        user.setBalance(user.getBalance().add(refundAmount));
        if (order.getPrevStatus() != null && order.getPrevStatus() >= 3) {
            int pts = order.getPayAmount() != null ? order.getPayAmount().intValue() : 0;
            int cur = user.getPoints() != null ? user.getPoints() : 0;
            user.setPoints(Math.max(0, cur - pts));
        }
        userMapper.updateById(user);
    }

    private void saveStatusLog(Long orderId, Integer from, Integer to,
                               Long operatorId, String role, String remark) {
        OrderStatusLog log = new OrderStatusLog();
        log.setOrderId(orderId);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setOperatorId(operatorId);
        log.setOperatorRole(role);
        log.setRemark(remark);
        orderStatusLogMapper.insert(log);
    }

    private String statusDesc(Integer code) {
        OrderStatus os = OrderStatus.of(code);
        return os != null ? os.getDesc() : String.valueOf(code);
    }

    /** 回滚订单对应商品的库存 */
    private void rollbackStock(Long orderId) {
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        for (OrderItem oi : items) {
            if (oi.getSkuId() != null && oi.getSkuId() != 0) {
                productSkuMapper.update(null, new LambdaUpdateWrapper<ProductSku>()
                        .eq(ProductSku::getId, oi.getSkuId())
                        .setSql("stock = stock + " + oi.getQuantity()));
            } else {
                productMapper.update(null, new LambdaUpdateWrapper<Product>()
                        .eq(Product::getId, oi.getProductId())
                        .setSql("stock = stock + " + oi.getQuantity()));
            }
        }
    }
}
