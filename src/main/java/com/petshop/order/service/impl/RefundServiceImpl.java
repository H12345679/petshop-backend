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

    @Override
    @Transactional
    public Map<String, Object> applyRefund(Long orderId, BigDecimal amount, String reason,
                                           Integer refundType, Integer received,
                                           String description, List<String> images) {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new BusinessException(ResultCode.UNAUTHORIZED);

        // 1) 权限与存在性校验：确保要退款的订单是这个用户本人的
        Order order = orderMapper.selectById(orderId);
        if (order == null) throw new BusinessException(ResultCode.NOT_FOUND);
        if (!userId.equals(order.getUserId())) throw new BusinessException(ResultCode.FORBIDDEN);

        // 2) 订单状态校验：只有处于【待收货】、【已收货】、【已评价】这三种售后期的订单，才允许发起退单申请
        int currentStatus = order.getStatus();
        validateRefundableStatus(currentStatus);

        // 3) 智能纠错与类型判定 (type 1:仅退款, 2:退货退款)
        int[] typeAndRecv = determineRefundTypeAndReceived(currentStatus, refundType, received);
        int type = typeAndRecv[0];
        int recv = typeAndRecv[1];

        // 4) 金额防刷校验：不管用户填多少，退款金额绝不能超过当时买东西时实际掏的钱
        BigDecimal refundAmount = (amount != null && amount.compareTo(order.getPayAmount()) <= 0)
                ? amount : order.getPayAmount();

        // 5) 创建退款工单：记录用户的申请理由和凭证，初始状态设为 0 (等待商家审核)
        Refund refund = new Refund();
        refund.setRefundNo(nextRefundNo());
        refund.setOrderId(orderId);
        refund.setUserId(userId);
        refund.setAmount(refundAmount);
        refund.setReason(reason);
        refund.setDescription(description);
        refund.setImages(toJsonArray(images));
        refund.setType(1);   // 1代表是"用户自己发起的申请"
        refund.setRefundType(type);
        refund.setReceived(recv);
        refund.setStatus(0); // 申请中
        this.save(refund);

        // 6) 冻结主订单：先把主订单原本的状态（比如"待收货"）备份到 prevStatus 里，然后将其标记为 -2 (退款售后中)。
        // 这样可以防止用户在退款扯皮期间，又手贱去点击"确认收货"或者"去评价"，从而避免整个交易状态乱套。
        order.setPrevStatus(currentStatus);
        order.setStatus(-2);
        orderMapper.updateById(order);

        // 7) 记录操作日志，留档备查
        String logRemark = resolveRefundLabel(type, recv) + reason;
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
            throw new BusinessException("退单不在待确认收货状态");
        }

        Order order = orderMapper.selectById(refund.getOrderId());
        if (order == null) throw new BusinessException("关联订单不存在");
        ownershipChecker.assertOrderOwned(order.getId());

        int from = order.getStatus();
        OrderStatus.checkTransition(from, -3);

        // 快递退款已在审核通过时打款，无需再次退款
        boolean isCourierRefund = refund.getRefundType() != null && refund.getRefundType() == 1
                && refund.getReceived() != null && refund.getReceived() == 0;
        if (!isCourierRefund) {
            BigDecimal refundAmount = refund.getAmount();
            if (refundAmount.compareTo(order.getPayAmount()) > 0) {
                refundAmount = order.getPayAmount();
            }
            refundToBalance(order, refundAmount);
        }

        refund.setStatus(1);
        appendAuditRemark(refund, "确认收货：", remark);
        this.updateById(refund);

        order.setStatus(-3);
        orderMapper.updateById(order);

        String logMsg;
        if (isCourierRefund) {
            logMsg = "商家确认已收到快递公司退回货物，库存已恢复";
        } else {
            logMsg = "商家确认收到退货（单号 " + refund.getReturnTrackingNumber() + "），退款完成";
        }
        saveStatusLog(order.getId(), from, -3, operatorId, role, logMsg);
        rollbackStock(order.getId());
    }

    private void appendAuditRemark(Refund refund, String prefix, String remark) {
        if (remark == null || remark.trim().isEmpty()) return;
        String old = refund.getAuditRemark();
        String base = (old != null && !old.isEmpty()) ? old + ";" : "";
        refund.setAuditRemark(base + prefix + remark.trim());
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

        saveStatusLog(orderId, currentStatus, -4, operatorId, role, reason);
        // 管理员直退针对已收货订单，用户保留商品，库存不恢复
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

        // 主订单继续保持被冻结的 -2 状态，在此环节只追加一条日志
        String remarkSuffix = (auditRemark != null && !auditRemark.isEmpty()) ? "：" + auditRemark : "";
        saveStatusLog(order.getId(), -2, -2, operatorId, role,
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
            // 快递退款：用户未收到货，退款已打回余额，但库存需等商家确认快递公司退回后才恢复
            refund.setStatus(4);
            this.updateById(refund);
            String remarkSuffix = (auditRemark != null && !auditRemark.isEmpty()) ? "：" + auditRemark : "";
            saveStatusLog(order.getId(), -2, -2, operatorId, role,
                    "快递退款已打款，待确认快递公司退回货物后恢复库存" + remarkSuffix);
        } else {
            // 收到货仅退款：用户保留商品，退款打回余额，库存不恢复
            int from = order.getStatus();
            OrderStatus.checkTransition(from, -3);
            refund.setStatus(1);
            this.updateById(refund);
            order.setStatus(-3);
            orderMapper.updateById(order);
            saveStatusLog(order.getId(), from, -3, operatorId, role, auditRemark);
        }
    }

    /** 场景C：驳回退款请求，恢复订单原状态 */
    private void handleRejectRefund(Refund refund, Order order,
                                    Long operatorId, String role, String auditRemark) {
        int from = order.getStatus();
        Integer prev = order.getPrevStatus();

        // 确保我们还能找到退款前的状态，否则没法恢复
        if (prev == null || (prev != 2 && prev != 3 && prev != 4)) {
            throw new BusinessException("订单数据异常，无法恢复原状态");
        }
        OrderStatus.checkTransition(from, prev);

        // 1. 退单盖上"已拒绝"的印章
        refund.setStatus(2); // 审核拒绝
        refund.setAuditUserId(operatorId);
        refund.setAuditTime(LocalDateTime.now(ZoneId.systemDefault()));
        refund.setAuditRemark(auditRemark);
        this.updateById(refund);

        // 2. 【核心动作：解冻主订单】
        // 把之前备份的 prevStatus 取出来还原给订单，同时清空备份字段。
        // 这样订单就又变回"待收货"或"已收货"了，生命周期继续往下走。
        order.setStatus(prev);
        order.setPrevStatus(null);
        orderMapper.updateById(order);

        saveStatusLog(order.getId(), from, prev, operatorId, role, "驳回：" + auditRemark);
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
        populateOrderItems(vo, order.getId());
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
    private void populateOrderItems(Map<String, Object> vo, Long orderId) {
        LambdaQueryWrapper<OrderItem> oiWrapper = new LambdaQueryWrapper<>();
        oiWrapper.eq(OrderItem::getOrderId, orderId);
        List<OrderItem> items = orderItemMapper.selectList(oiWrapper);

        BigDecimal maxRefund = BigDecimal.ZERO;
        String productName = "—";
        if (!items.isEmpty()) {
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
