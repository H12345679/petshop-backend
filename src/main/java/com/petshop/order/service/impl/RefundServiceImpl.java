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
import java.time.ZoneId;
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

    /**
     * 申请退款
     * 支持部分退款和整单退款。若所有明细都在退款中或已退款，则冻结整单。
     */
    @Override
    @Transactional
    public Map<String, Object> applyRefund(Long orderId, Long orderItemId, BigDecimal amount, String reason,
                                           Integer refundType, Integer received,
                                           String description, List<String> images) {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new BusinessException(ResultCode.UNAUTHORIZED);

        Order order = orderMapper.selectById(orderId);
        if (order == null) throw new BusinessException(ResultCode.NOT_FOUND);
        if (!userId.equals(order.getUserId())) throw new BusinessException(ResultCode.FORBIDDEN);

        int currentStatus = order.getStatus();
        // 部分退款中订单保持原状态，允许同一订单其他明细继续申请退款
        if (currentStatus != -2) {
            validateRefundableStatus(currentStatus);
        }

        int[] typeAndRecv = determineRefundTypeAndReceived(
                currentStatus == -2 ? order.getPrevStatus() : currentStatus, refundType, received);
        int type = typeAndRecv[0];
        int recv = typeAndRecv[1];

        // 查询订单明细
        List<OrderItem> allItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));

        // 定位退款明细
        OrderItem targetItem = resolveTargetItem(allItems, orderItemId);
        if (targetItem.getRefundStatus() != null && targetItem.getRefundStatus() != 0) {
            throw new BusinessException("该商品已在退款流程中，请勿重复申请");
        }
        if (targetItem.getCancelStatus() != null && targetItem.getCancelStatus() > 0) {
            throw new BusinessException("该商品已取消，无法申请退款");
        }

        // 金额上限取明细实付
        BigDecimal itemMax = targetItem.getRealPayAmount() != null ? targetItem.getRealPayAmount() : BigDecimal.ZERO;
        BigDecimal refundAmount = (amount != null && amount.compareTo(itemMax) <= 0) ? amount : itemMax;

        Refund refund = new Refund();
        refund.setRefundNo(nextRefundNo());
        refund.setOrderId(orderId);
        refund.setOrderItemId(targetItem.getId());
        refund.setUserId(userId);
        refund.setAmount(refundAmount);
        refund.setReason(reason);
        refund.setDescription(description);
        refund.setImages(toJsonArray(images));
        refund.setType(1);
        refund.setRefundType(type);
        refund.setReceived(recv);
        refund.setStatus(0);
        this.save(refund);

        // 标记该明细进入退款流程
        targetItem.setRefundStatus(1);
        orderItemMapper.updateById(targetItem);

        // 判断是否需要冻结整单：所有明细都在退款中(1)或已退款(2)时才冻结
        boolean allInRefund = allItems.stream().allMatch(i ->
                i.getId().equals(targetItem.getId()) || isItemInRefundOrDone(i));
        if (allInRefund && currentStatus != -2) {
            order.setPrevStatus(currentStatus);
            order.setStatus(-2);
            orderMapper.updateById(order);
            saveStatusLog(orderId, currentStatus, -2, userId, "USER",
                    resolveRefundLabel(type, recv) + reason);
        } else {
            int logFrom = currentStatus == -2 ? -2 : currentStatus;
            saveStatusLog(orderId, logFrom, logFrom, userId, "USER",
                    "[部分退款] " + targetItem.getProductName() + " " + resolveRefundLabel(type, recv) + reason);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("refundNo", refund.getRefundNo());
        result.put("refundId", refund.getId());
        return result;
    }

    /**
     * 定位退款明细。
     * 如果指定了明细ID则校验并返回，如果没有指定但订单只有一个明细也自动返回。
     * 否则抛出异常要求选择明细。
     *
     * @param allItems    订单下所有明细
     * @param orderItemId 指定退款的明细ID
     * @return 匹配的明细对象
     */
    private OrderItem resolveTargetItem(List<OrderItem> allItems, Long orderItemId) {
        if (allItems.isEmpty()) throw new BusinessException("订单明细为空");
        if (orderItemId != null) {
            return allItems.stream()
                    .filter(i -> i.getId().equals(orderItemId))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("指定的订单明细不存在"));
        }
        // 未指定明细且只有一项 → 退该项
        if (allItems.size() == 1) return allItems.get(0);
        throw new BusinessException("该订单含多个商品，请选择要退款的商品");
    }

    /**
     * 判断订单明细是否在退款流程中或者已经退款完毕
     *
     * @param item 订单明细
     * @return true如果在退款流程中或已完成
     */
    private boolean isItemInRefundOrDone(OrderItem item) {
        Integer rs = item.getRefundStatus();
        if (rs != null && rs > 0) return true;
        Integer cs = item.getCancelStatus();
        return cs != null && cs > 0;
    }

    /**
     * 商家审核退款申请
     * @param status 1-同意退款，2-驳回退款
     */
    @Override
    @Transactional
    public void auditRefund(Long refundId, Integer status, String auditRemark) {
        Long operatorId = UserContext.getUserId();
        String role = UserContext.getRole();
        if (operatorId == null) throw new BusinessException(ResultCode.UNAUTHORIZED);

        // 1) 校验退单存不存在、是不是还在等待处理阶段（0）
        Refund refund = this.getById(refundId);
        if (refund == null) throw new BusinessException(ResultCode.NOT_FOUND);
        if (refund.getStatus() == null || refund.getStatus() != 0) {
            throw new BusinessException("该退单已被处理，请勿重复操作");
        }

        // 2) 校验商家权限，确保商家只能审核属于自己店铺的退单
        Order order = orderMapper.selectById(refund.getOrderId());
        if (order == null) throw new BusinessException("关联订单不存在");
        ownershipChecker.assertOrderOwned(order.getId());

        if (status != null && status == 1) {
            // 商家点击了【同意】按钮
            handleApproveRefund(refund, order, operatorId, role, auditRemark);
        } else {
            // 商家【驳回（拒绝）】退款请求
            handleRejectRefund(refund, order, operatorId, role, auditRemark);
        }
    }

    /**
     * 用户提交退货物流信息
     */
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
        refund.setReturnTime(LocalDateTime.now(ZoneId.systemDefault()));
        refund.setStatus(4); // 待商家确认收货
        this.updateById(refund);

        Order order = orderMapper.selectById(refund.getOrderId());
        int logStatus = (order != null) ? order.getStatus() : -2;
        saveStatusLog(refund.getOrderId(), logStatus, logStatus, userId, "USER",
                "用户已寄回退货，快递：" + (courierCompany != null ? courierCompany : "") + " " + trackingNumber.trim());
    }

    /**
     * 商家确认收货，完成打款
     */
    @Override
    @Transactional
    public void confirmReturnReceived(Long refundId, String remark) {
        Long operatorId = UserContext.getUserId();
        String role = UserContext.getRole();
        if (operatorId == null) throw new BusinessException(ResultCode.UNAUTHORIZED);

        Refund refund = validateRefundForConfirm(refundId);
        Order order = validateOrderForConfirm(refund);

        boolean isCourierRefund = isCourierRefund(refund);
        if (!isCourierRefund) {
            refundBalanceCapped(order, refund.getAmount());
        }

        refund.setStatus(1);
        appendAuditRemark(refund, "确认收货：", remark);
        this.updateById(refund);

        markItemRefunded(refund.getOrderItemId());
        rollbackStockForItem(refund.getOrderItemId(), order.getId());
        resolveOrderAfterItemRefund(order, operatorId, role, buildConfirmLogMsg(isCourierRefund, refund));
    }

    /**
     * 确认收货时，校验退款单的有效性和状态
     *
     * @param refundId 退款单ID
     * @return 有效的退款单对象
     */
    private Refund validateRefundForConfirm(Long refundId) {
        Refund refund = this.getById(refundId);
        if (refund == null) throw new BusinessException(ResultCode.NOT_FOUND);
        if (refund.getStatus() == null || refund.getStatus() != 4) {
            throw new BusinessException("退单不在待确认收货状态");
        }
        return refund;
    }

    /**
     * 确认收货时，校验对应订单是否存在以及是否属于当前操作商家
     *
     * @param refund 退款单对象
     * @return 关联的订单对象
     */
    private Order validateOrderForConfirm(Refund refund) {
        Order order = orderMapper.selectById(refund.getOrderId());
        if (order == null) throw new BusinessException("关联订单不存在");
        ownershipChecker.assertOrderOwned(order.getId());
        return order;
    }

    /**
     * 判断是否属于快递退回退款场景（用户未收到货发起的仅退款）
     *
     * @param refund 退款单对象
     * @return true如果是快递退款
     */
    private boolean isCourierRefund(Refund refund) {
        return Integer.valueOf(1).equals(refund.getRefundType())
                && Integer.valueOf(0).equals(refund.getReceived());
    }

    /**
     * 执行余额退款操作（带金额上限控制，最大不超过订单实付）
     *
     * @param order  订单对象
     * @param amount 申请退款金额
     */
    private void refundBalanceCapped(Order order, BigDecimal amount) {
        BigDecimal capped = amount.compareTo(order.getPayAmount()) > 0 ? order.getPayAmount() : amount;
        refundToBalance(order, capped);
    }

    /**
     * 构建确认收货的日志描述信息
     *
     * @param isCourierRefund 是否快递退回退款
     * @param refund          退款单对象
     * @return 日志描述
     */
    private String buildConfirmLogMsg(boolean isCourierRefund, Refund refund) {
        if (isCourierRefund) {
            return "商家确认已收到快递公司退回货物，库存已恢复";
        }
        return "商家确认收到退货（单号 " + refund.getReturnTrackingNumber() + "），退款完成";
    }

    /**
     * 追加审核备注信息，使用分号分隔
     *
     * @param refund 退款单
     * @param prefix 备注前缀
     * @param remark 备注内容
     */
    private void appendAuditRemark(Refund refund, String prefix, String remark) {
        if (remark == null || remark.trim().isEmpty()) return;
        String old = refund.getAuditRemark();
        String base = (old != null && !old.isEmpty()) ? old + ";" : "";
        refund.setAuditRemark(base + prefix + remark.trim());
    }

    /**
     * 后台管理员或系统直接退款（跳过审核）
     */
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
            throw new BusinessException("已收货的订单才可直接退单（当前：" + statusDesc(currentStatus) + ")");
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
        refund.setAuditTime(LocalDateTime.now(ZoneId.systemDefault()));
        refund.setAuditRemark(reason);
        this.save(refund);

        // 退回余额；直接退款仅从已收货(3)发起，收货赠送的积分一并扣回
        order.setPrevStatus(currentStatus);
        refundToBalance(order, order.getPayAmount());

        order.setStatus(-4);
        orderMapper.updateById(order);

        // 标记所有明细为已退款
        orderItemMapper.update(null, new LambdaUpdateWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId)
                .set(OrderItem::getRefundStatus, 2));

        saveStatusLog(orderId, currentStatus, -4, operatorId, role, reason);
    }

    /**
     * 根据订单ID或订单号进行直接退款
     */
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

    /**
     * 退款记录分页查询（管理端）
     */
    @Override
    public PageResult<Map<String, Object>> managePage(int current, int size, Long shopId,
                                                      Integer status, String refundNo, String username) {
        LambdaQueryWrapper<Refund> wrapper = buildRefundQueryWrapper(status, refundNo, username);
        Page<Refund> page = this.page(new Page<>(current, size), wrapper);

        List<Long> shopIds = ownershipChecker.myShopIds();
        List<Map<String, Object>> records = new ArrayList<>();

        for (Refund r : page.getRecords()) {
            Map<String, Object> vo = buildRefundRecord(r, shopIds);
            if (vo != null) {
                records.add(vo);
            }
        }

        PageResult<Map<String, Object>> pr = new PageResult<>();
        pr.setTotal(shopIds != null ? records.size() : page.getTotal());
        pr.setPages(shopIds != null ? 1 : page.getPages());
        pr.setCurrent(current);
        pr.setSize(size);
        pr.setRecords(records);
        return pr;
    }

    // ========== 内部辅助方法 ==========

    /** 校验订单状态是否允许申请退款 */
    private void validateRefundableStatus(int currentStatus) {
        if (currentStatus != 2 && currentStatus != 3 && currentStatus != 4) {
            throw new BusinessException("当前订单状态不可申请退款（" + statusDesc(currentStatus) + "）");
        }
    }

    /** 智能判定退款类型(1仅退款/2退货退款)和收货状态 */
    private int[] determineRefundTypeAndReceived(int currentStatus, Integer refundType, Integer received) {
        int type = (refundType != null && refundType == 2) ? 2 : 1;
        // 如果订单处于待收货状态，并且用户声明"未收到货"（比如快递丢了），强制走"仅退款"
        int recv = (currentStatus == 2 && received != null && received == 0) ? 0 : 1;
        if (recv == 0) {
            type = 1;
        }
        // 如果订单都已经评价了，说明货肯定已经收到，此时只能走"退货退款"
        if (currentStatus == 4 && type != 2) {
            throw new BusinessException("已评价的订单退款必须退货，请选择退货退款");
        }
        return new int[]{type, recv};
    }

    /** 返回退款类型对应的日志标签 */
    private String resolveRefundLabel(int type, int recv) {
        if (type == 2) {
            return "[退货退款] ";
        }
        if (recv == 0) {
            return "[仅退款·未收到货] ";
        }
        return "[仅退款] ";
    }

    /** 商家同意退款：根据是否需要退货，分派到不同处理流程 */
    private void handleApproveRefund(Refund refund, Order order,
                                     Long operatorId, String role, String auditRemark) {
        boolean needReturn = refund.getRefundType() != null && refund.getRefundType() == 2;
        if (needReturn) {
            handleApproveReturn(refund, order, operatorId, role, auditRemark);
        } else {
            handleApproveRefundOnly(refund, order, operatorId, role, auditRemark);
        }
    }

    /** 场景A：退货退款 -- 商家同意退货，等待用户寄回 */
    private void handleApproveReturn(Refund refund, Order order,
                                     Long operatorId, String role, String auditRemark) {
        refund.setStatus(3); // 3: 待用户退货
        refund.setAuditUserId(operatorId);
        refund.setAuditTime(LocalDateTime.now(ZoneId.systemDefault()));
        refund.setAuditRemark(auditRemark);
        this.updateById(refund);

        int from = order.getStatus();
        String remarkSuffix = (auditRemark != null && !auditRemark.isEmpty()) ? "：" + auditRemark : "";
        saveStatusLog(order.getId(), from, from, operatorId, role,
                "同意退货，待用户寄回并填写退货单号" + remarkSuffix);
    }

    /** 场景B：仅退款 -- 商家同意，直接执行退款打款 */
    private void handleApproveRefundOnly(Refund refund, Order order,
                                         Long operatorId, String role, String auditRemark) {
        BigDecimal refundAmount = refund.getAmount();
        if (refundAmount.compareTo(order.getPayAmount()) > 0) {
            refundAmount = order.getPayAmount();
        }

        refundToBalance(order, refundAmount);

        refund.setAuditUserId(operatorId);
        refund.setAuditTime(LocalDateTime.now(ZoneId.systemDefault()));
        refund.setAuditRemark(auditRemark);

        boolean courierRefund = refund.getReceived() != null && refund.getReceived() == 0;
        if (courierRefund) {
            refund.setStatus(4);
            this.updateById(refund);
            int from = order.getStatus();
            String remarkSuffix = (auditRemark != null && !auditRemark.isEmpty()) ? "：" + auditRemark : "";
            saveStatusLog(order.getId(), from, from, operatorId, role,
                    "快递退款已打款，待确认快递公司退回货物后恢复库存" + remarkSuffix);
        } else {
            // 收到货仅退款：退款到账，标记明细已退款
            refund.setStatus(1);
            this.updateById(refund);
            markItemRefunded(refund.getOrderItemId());
            resolveOrderAfterItemRefund(order, operatorId, role, auditRemark);
        }
    }

    /** 场景C：驳回退款请求，恢复订单原状态 */
    private void handleRejectRefund(Refund refund, Order order,
                                    Long operatorId, String role, String auditRemark) {
        refund.setStatus(2);
        refund.setAuditUserId(operatorId);
        refund.setAuditTime(LocalDateTime.now(ZoneId.systemDefault()));
        refund.setAuditRemark(auditRemark);
        this.updateById(refund);

        // 恢复明细退款状态
        resetItemRefundStatus(refund.getOrderItemId());

        int from = order.getStatus();
        // 检查是否还有其他明细在退款中
        boolean otherItemsInRefund = hasOtherActiveRefunds(order.getId(), refund.getOrderItemId());
        if (from == -2 && !otherItemsInRefund) {
            Integer prev = order.getPrevStatus();
            if (prev == null || (prev != 2 && prev != 3 && prev != 4)) {
                throw new BusinessException("订单数据异常，无法恢复原状态");
            }
            OrderStatus.checkTransition(from, prev);
            order.setStatus(prev);
            order.setPrevStatus(null);
            orderMapper.updateById(order);
            saveStatusLog(order.getId(), from, prev, operatorId, role, "驳回：" + auditRemark);
        } else {
            saveStatusLog(order.getId(), from, from, operatorId, role, "驳回：" + auditRemark);
        }
    }

    /** 构建退款列表查询条件 */
    private LambdaQueryWrapper<Refund> buildRefundQueryWrapper(Integer status, String refundNo, String username) {
        LambdaQueryWrapper<Refund> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Refund::getStatus, status);
        }
        if (refundNo != null && !refundNo.isEmpty()) {
            wrapper.eq(Refund::getRefundNo, refundNo);
        }
        if (username != null && !username.isEmpty()) {
            applyUsernameFilter(wrapper, username);
        }
        wrapper.orderByDesc(Refund::getCreateTime);
        return wrapper;
    }

    /** 按用户名/昵称模糊匹配过滤退款记录 */
    private void applyUsernameFilter(LambdaQueryWrapper<Refund> wrapper, String username) {
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

    /** 构建单条退款记录的VO Map；若关联订单不存在或不属于当前商家则返回null */
    private Map<String, Object> buildRefundRecord(Refund r, List<Long> shopIds) {
        Order order = orderMapper.selectById(r.getOrderId());
        if (order == null) {
            return null;
        }
        // MERCHANT 按店铺过滤
        if (shopIds != null && !shopIds.contains(order.getShopId())) {
            return null;
        }

        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("id", r.getId());
        vo.put("refundNo", r.getRefundNo());
        vo.put("orderId", r.getOrderId());
        vo.put("orderNo", order.getOrderNo());
        vo.put("userId", r.getUserId());
        // 买家名
        User user = userMapper.selectById(r.getUserId());
        vo.put("buyerName", resolveBuyerName(user, r.getUserId()));
        vo.put("amount", r.getAmount());
        vo.put("reason", r.getReason());
        vo.put("description", r.getDescription());
        vo.put("images", r.getImages());
        vo.put("type", r.getType());
        vo.put("orderItemId", r.getOrderItemId());
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
        populateOrderItems(vo, order.getId(), r.getOrderItemId());
        return vo;
    }

    /** 解析买家显示名称 */
    private String resolveBuyerName(User user, Long userId) {
        if (user == null) {
            return "用户#" + userId;
        }
        return user.getNickname() != null ? user.getNickname() : user.getUsername();
    }

    /** 查订单明细，填充商品名和可退上限到VO */
    private void populateOrderItems(Map<String, Object> vo, Long orderId, Long orderItemId) {
        LambdaQueryWrapper<OrderItem> oiWrapper = new LambdaQueryWrapper<>();
        oiWrapper.eq(OrderItem::getOrderId, orderId);
        List<OrderItem> items = orderItemMapper.selectList(oiWrapper);

        BigDecimal maxRefund = BigDecimal.ZERO;
        String productName = "—";
        if (orderItemId != null) {
            // 按明细退款 — 只显示该明细
            for (OrderItem oi : items) {
                if (oi.getId().equals(orderItemId)) {
                    productName = oi.getProductName() != null ? oi.getProductName() : "商品";
                    maxRefund = oi.getRealPayAmount() != null ? oi.getRealPayAmount() : BigDecimal.ZERO;
                    break;
                }
            }
        } else if (!items.isEmpty()) {
            OrderItem firstItem = items.get(0);
            productName = firstItem.getProductName() != null ? firstItem.getProductName() : "商品";
            if (items.size() > 1) {
                productName += " 等" + items.size() + "件";
            }
            for (OrderItem oi : items) {
                BigDecimal rpa = oi.getRealPayAmount() != null ? oi.getRealPayAmount() : BigDecimal.ZERO;
                maxRefund = maxRefund.add(rpa);
            }
        }
        vo.put("productName", productName);
        vo.put("maxRefund", maxRefund);
    }

    /** 生成退单号：RFD + yyyyMMdd + 毫秒后6位 */
    private String nextRefundNo() {
        return REFUND_NO_PREFIX + LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyyMMdd"))
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
        Integer prevStatus = order.getPrevStatus() != null ? order.getPrevStatus() : order.getStatus();
        if (prevStatus >= 3) {
            // 按退款金额比例扣回积分
            int pts = refundAmount.intValue();
            int cur = user.getPoints() != null ? user.getPoints() : 0;
            user.setPoints(Math.max(0, cur - pts));
        }
        userMapper.updateById(user);
    }

    /**
     * 保存订单状态流转日志
     *
     * @param orderId    订单ID
     * @param from       变更前状态
     * @param to         变更后状态
     * @param operatorId 操作人ID
     * @param role       操作人角色
     * @param remark     备注
     */
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

    /**
     * 获取订单状态对应的文本描述
     *
     * @param code 状态码
     * @return 状态描述
     */
    private String statusDesc(Integer code) {
        OrderStatus os = OrderStatus.of(code);
        return os != null ? os.getDesc() : String.valueOf(code);
    }

    /** 标记某个订单明细为已退款 */
    private void markItemRefunded(Long orderItemId) {
        if (orderItemId == null) return;
        orderItemMapper.update(null, new LambdaUpdateWrapper<OrderItem>()
                .eq(OrderItem::getId, orderItemId)
                .set(OrderItem::getRefundStatus, 2));
    }

    /** 驳回时恢复明细退款状态为正常 */
    private void resetItemRefundStatus(Long orderItemId) {
        if (orderItemId == null) return;
        orderItemMapper.update(null, new LambdaUpdateWrapper<OrderItem>()
                .eq(OrderItem::getId, orderItemId)
                .set(OrderItem::getRefundStatus, 0));
    }

    /** 检查该订单是否还有其他明细正在退款(排除指定明细) */
    private boolean hasOtherActiveRefunds(Long orderId, Long excludeItemId) {
        LambdaQueryWrapper<Refund> w = new LambdaQueryWrapper<Refund>()
                .eq(Refund::getOrderId, orderId)
                .in(Refund::getStatus, 0, 3, 4);
        if (excludeItemId != null) {
            w.ne(Refund::getOrderItemId, excludeItemId);
        }
        return this.count(w) > 0;
    }

    /** 某明细退款完成后，判断整单状态：全部退完→-3，否则恢复原状态 */
    private void resolveOrderAfterItemRefund(Order order, Long operatorId, String role, String logRemark) {
        int from = order.getStatus();
        boolean allDone = allItemsRefunded(order.getId());
        if (allDone) {
            int to = -3;
            if (from != -3) {
                if (from != -2) {
                    order.setPrevStatus(from);
                }
                order.setStatus(to);
                orderMapper.updateById(order);
            }
            saveStatusLog(order.getId(), from, -3, operatorId, role, logRemark);
        } else if (from == -2) {
            // 还有其他未退款的明细在走退款流程，保持-2
            boolean anyActive = hasOtherActiveRefunds(order.getId(), null);
            if (anyActive) {
                saveStatusLog(order.getId(), from, from, operatorId, role, logRemark);
            } else {
                // 没有在途退款了但还有正常明细，恢复订单
                Integer prev = order.getPrevStatus();
                if (prev != null && prev > 0) {
                    order.setStatus(prev);
                    order.setPrevStatus(null);
                    orderMapper.updateById(order);
                    saveStatusLog(order.getId(), from, prev, operatorId, role, logRemark);
                } else {
                    saveStatusLog(order.getId(), from, from, operatorId, role, logRemark);
                }
            }
        } else {
            // 订单未冻结（部分退款），保持当前状态
            saveStatusLog(order.getId(), from, from, operatorId, role, logRemark);
        }
    }

    /** 检查订单所有明细是否都已退款完成 */
    private boolean allItemsRefunded(Long orderId) {
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        return items.stream().allMatch(i ->
                Integer.valueOf(2).equals(i.getRefundStatus())
                        || (i.getCancelStatus() != null && i.getCancelStatus() > 0));
    }

    /** 回滚单个订单明细的库存 */
    private void rollbackStockForItem(Long orderItemId, Long orderId) {
        if (orderItemId != null) {
            OrderItem oi = orderItemMapper.selectById(orderItemId);
            if (oi != null) {
                rollbackSingleItem(oi);
                return;
            }
        }
        rollbackStock(orderId);
    }

    /**
     * 回滚订单下单时扣减的指定商品库存
     *
     * @param oi 订单明细
     */
    private void rollbackSingleItem(OrderItem oi) {
        if (oi.getSkuId() != null && oi.getSkuId() != 0) {
            productSkuMapper.update(null, new LambdaUpdateWrapper<ProductSku>()
                    .eq(ProductSku::getId, oi.getSkuId())
                    .setSql("stock = stock + " + oi.getQuantity()));
        }
        productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, oi.getProductId())
                .setSql("stock = stock + " + oi.getQuantity()));
    }

    /** 回滚订单所有商品的库存（整单退款兼容） */
    private void rollbackStock(Long orderId) {
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        for (OrderItem oi : items) {
            if (oi.getSkuId() != null && oi.getSkuId() != 0) {
                productSkuMapper.update(null, new LambdaUpdateWrapper<ProductSku>()
                        .eq(ProductSku::getId, oi.getSkuId())
                        .setSql("stock = stock + " + oi.getQuantity()));
                // 同步回滚主表 stock（主表 stock = 所有 SKU 库存之和）
                productMapper.update(null, new LambdaUpdateWrapper<Product>()
                        .eq(Product::getId, oi.getProductId())
                        .setSql("stock = stock + " + oi.getQuantity()));
            } else {
                productMapper.update(null, new LambdaUpdateWrapper<Product>()
                        .eq(Product::getId, oi.getProductId())
                        .setSql("stock = stock + " + oi.getQuantity()));
            }
        }
    }
}
