package com.petshop.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petshop.common.BusinessException;
import com.petshop.common.OrderStatus;
import com.petshop.common.PageResult;
import com.petshop.common.ResultCode;
import com.petshop.order.entity.*;
import com.petshop.order.mapper.*;
import com.petshop.order.service.OrderService;
import com.petshop.product.entity.Product;
import com.petshop.product.entity.ProductSku;
import com.petshop.product.mapper.ProductMapper;
import com.petshop.product.mapper.ProductSkuMapper;
import com.petshop.security.OwnershipChecker;
import com.petshop.security.UserContext;
import com.petshop.shop.entity.Shop;
import com.petshop.shop.mapper.ShopMapper;
import com.petshop.shop.mapper.ShopCustomerMapper;
import com.petshop.user.entity.Address;
import com.petshop.user.entity.User;
import com.petshop.user.mapper.AddressMapper;
import com.petshop.user.mapper.UserMapper;
import com.petshop.content.entity.UserBehavior;
import com.petshop.content.mapper.UserBehaviorMapper;
import com.petshop.config.RabbitMQConfig;
import com.petshop.recommend.model.dto.UserBehaviorMessage;
import com.petshop.util.RedisUtil;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 订单 Service 实现
 * <p>
 * 涵盖：结算预览、下单（跨店拆单 + 幂等 + CAS 锁券 + 库存扣减 + 优惠分摊）、
 * 我的订单、后台订单管理。
 */
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    @Autowired
    private CartItemMapper cartItemMapper;
    @Autowired
    private com.petshop.user.service.MembershipLevelService membershipLevelService;
    @Autowired
    private OrderItemMapper orderItemMapper;
    @Autowired
    private OrderStatusLogMapper orderStatusLogMapper;
    @Autowired
    private UserBehaviorMapper userBehaviorMapper;
    @Autowired
    private CouponMapper couponMapper;
    @Autowired
    private UserCouponMapper userCouponMapper;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private ProductSkuMapper productSkuMapper;
    @Autowired
    private AddressMapper addressMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ShopMapper shopMapper;
    @Autowired
    private ShopCustomerMapper shopCustomerMapper;
    @Autowired
    private RedisUtil redisUtil;
    @Autowired
    private OwnershipChecker ownershipChecker;
    @Autowired
    private RefundMapper refundMapper;
    /** 直接注入 RedisTemplate 用于 increment 操作 */
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    /** 购买行为发 MQ，供推荐系统实时画像消费（与浏览/收藏/加购口径一致） */
    @Autowired
    private RabbitTemplate rabbitTemplate;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TOTAL_AMOUNT = "totalAmount";
    private static final String DISCOUNT_AMOUNT = "discountAmount";
    private static final String PAY_AMOUNT = "payAmount";
    private static final String COUPON_ID = "couponId";
    private static final String STATUS = "status";
    private static final String REDIS_REQUEST_ID_PREFIX = "order:requestId:";
    private static final String REDIS_ORDER_NO_SEQ = "order:no:seq:";
    private static final int REQUEST_ID_TTL_MINUTES = 30;

    // ==================== 结算预览 ====================

    @Override
    public Map<String, Object> preSettle(List<Map<String, Object>> items, Long userCouponId, Long addressId) {
        Long userId = requireUserId();
        if (items == null || items.isEmpty()) {
            throw new BusinessException("购买项不能为空");
        }

        BigDecimal totalAmount = calculateItemsTotal(items);

        BigDecimal userDiscountRate = membershipLevelService.getCurrentUserDiscount();
        BigDecimal memberDiscount = totalAmount.multiply(BigDecimal.ONE.subtract(userDiscountRate));
        BigDecimal amountAfterMember = totalAmount.subtract(memberDiscount);

        BigDecimal couponDiscount = BigDecimal.ZERO;
        Long effectiveUserCouponId = 0L;
        Coupon validCoupon = validateUserCoupon(userCouponId, userId, totalAmount);
        if (validCoupon != null) {
            if (validCoupon.getType() == 1) {
                couponDiscount = validCoupon.getAmount();
            } else if (validCoupon.getType() == 2) {
                couponDiscount = amountAfterMember.multiply(
                        BigDecimal.ONE.subtract(validCoupon.getAmount()));
            }
            effectiveUserCouponId = userCouponId;
        }

        BigDecimal discountAmount = memberDiscount.add(couponDiscount);
        BigDecimal payAmount = totalAmount.subtract(discountAmount);
        if (payAmount.compareTo(BigDecimal.ZERO) < 0) payAmount = BigDecimal.ZERO;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put(TOTAL_AMOUNT, totalAmount);
        result.put(DISCOUNT_AMOUNT, discountAmount);
        result.put("memberDiscount", memberDiscount);
        result.put("couponDiscount", couponDiscount);
        result.put(PAY_AMOUNT, payAmount);
        result.put(COUPON_ID, effectiveUserCouponId);
        result.put("addressId", addressId);
        return result;
    }

    // ==================== 创建订单 ====================

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> createOrder(String requestId, Long userCouponId, Long addressId,
                                           List<Map<String, Object>> items, String remark) {
        Long userId = requireUserId();
        if (items == null || items.isEmpty()) {
            throw new BusinessException("购买项不能为空");
        }

        Map<String, Object> cached = checkIdempotency(requestId);
        if (cached != null) return cached;

        Address address = addressMapper.selectById(validateAddressId(addressId, userId));

        List<ItemLine> itemLines = buildItemLines(items);

        Map<Long, List<ItemLine>> grouped = itemLines.stream()
                .collect(Collectors.groupingBy(ItemLine::getShopId, LinkedHashMap::new, Collectors.toList()));

        BigDecimal totalOrderAmount = itemLines.stream()
                .map(line -> line.price.multiply(BigDecimal.valueOf(line.qty)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal userDiscountRate = membershipLevelService.getCurrentUserDiscount();
        BigDecimal totalMemberDiscount = totalOrderAmount.multiply(BigDecimal.ONE.subtract(userDiscountRate));
        BigDecimal amountAfterMember = totalOrderAmount.subtract(totalMemberDiscount);

        CouponLockResult couponResult = lockCoupon(userCouponId, userId, totalOrderAmount, amountAfterMember);

        BigDecimal totalPayAmount = totalOrderAmount.subtract(couponResult.discount).subtract(totalMemberDiscount);
        if (totalPayAmount.compareTo(BigDecimal.ZERO) < 0) totalPayAmount = BigDecimal.ZERO;

        String today = LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        List<Long> orderIds = new ArrayList<>();
        List<String> orderNos = new ArrayList<>();

        for (Map.Entry<Long, List<ItemLine>> entry : grouped.entrySet()) {
            createShopSubOrder(entry.getKey(), entry.getValue(), totalOrderAmount, totalPayAmount,
                    userCouponId, couponResult.usedUserCoupon, address, remark, userId, today, orderIds, orderNos);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderIds", orderIds);
        result.put("orderNos", orderNos);
        result.put("totalPayAmount", totalPayAmount);
        result.put("requestId", requestId);
        cacheIdempotentResult(requestId, result);
        return result;
    }

    // ==================== 我的订单 ====================

    @Override
    public PageResult<Map<String, Object>> myOrders(int current, int size, Integer status) {
        Long userId = requireUserId();
        LambdaQueryWrapper<Order> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Order::getUserId, userId);
        if (status != null) {
            queryWrapper.eq(Order::getStatus, status);
        }
        queryWrapper.orderByDesc(Order::getCreateTime);
        Page<Order> page = this.page(new Page<>(current, size), queryWrapper);
        return buildOrderPageResult(page);
    }

    @Override
    public Map<String, Object> getOrderById(Long orderId, Long userId) {
        Order order = this.getById(orderId);
        if (order == null) throw new BusinessException(ResultCode.NOT_FOUND);
        if (!userId.equals(order.getUserId())) throw new BusinessException(ResultCode.FORBIDDEN);
        return buildOrderMap(order);
    }

    // ==================== 后台订单管理 ====================

    @Override
    public PageResult<Map<String, Object>> manageOrders(int current, int size, Long shopId,
                                                         String orderNo, Integer status) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();

        // MERCHANT 只看自家店
        List<Long> myShopIds = ownershipChecker.myShopIds();
        if (myShopIds != null) {
            if (myShopIds.isEmpty()) {
                wrapper.eq(Order::getId, -1L);
            } else {
                wrapper.in(Order::getShopId, myShopIds);
            }
        } else if (shopId != null) {
            wrapper.eq(Order::getShopId, shopId);
        }
        if (orderNo != null && !orderNo.isEmpty()) {
            wrapper.eq(Order::getOrderNo, orderNo);
        }
        if (status != null) {
            wrapper.eq(Order::getStatus, status);
        } else {
            // Exclude orders in "Refund Processing" (-2) from the general order management list
            wrapper.ne(Order::getStatus, -2);
        }
        wrapper.orderByDesc(Order::getCreateTime);
        Page<Order> page = this.page(new Page<>(current, size), wrapper);
        return buildOrderPageResult(page);
    }

    // ==================== 订单流转：支付 / 取消 / 发货 / 收货 ====================

    @Override
    @Transactional
    public void pay(Long orderId, Integer payType) {
        Long userId = requireUserId();

        Order order = this.baseMapper.selectById(orderId);
        if (order == null) throw new BusinessException(ResultCode.NOT_FOUND);
        if (!userId.equals(order.getUserId())) throw new BusinessException(ResultCode.FORBIDDEN);
        if (order.getStatus() == null || order.getStatus() != 0) {
            throw new BusinessException("订单状态不允许支付（当前：" + statusDesc(order.getStatus()) + "）");
        }

        // CAS 抢占订单：status 0→1，影响行数!=1 说明已被并发支付/取消，防重复扣款
        int paidRows = this.baseMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, 0)
                .set(Order::getStatus, 1)
                .set(Order::getPayType, payType != null ? payType : 1)
                .set(Order::getPayTime, LocalDateTime.now(ZoneId.systemDefault())));
        if (paidRows != 1) {
            throw new BusinessException("订单状态不允许支付（可能已支付或已取消）");
        }

        // CAS 扣余额：WHERE balance>=payAmount 原子判断，失败抛错回滚（含上面的订单状态）
        int balRows = userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .ge(User::getBalance, order.getPayAmount())
                .setSql("balance = balance - " + order.getPayAmount().toPlainString()));
        if (balRows != 1) {
            throw new BusinessException("余额不足");
        }

        saveStatusLog(order.getId(), 0, 1, userId, "USER", "用户支付");
        recordPurchaseBehavior(userId, orderId);
        settleShopCustomer(order, userId);
    }

    private void recordPurchaseBehavior(Long userId, Long orderId) {
        LambdaQueryWrapper<OrderItem> oiWrapper = new LambdaQueryWrapper<>();
        oiWrapper.eq(OrderItem::getOrderId, orderId);
        List<OrderItem> items = orderItemMapper.selectList(oiWrapper);
        for (OrderItem item : items) {
            UserBehavior behavior = new UserBehavior();
            behavior.setUserId(userId);
            behavior.setProductId(item.getProductId());
            behavior.setBehaviorType(4);
            userBehaviorMapper.insert(behavior);
            try {
                rabbitTemplate.convertAndSend(RabbitMQConfig.RECOMMEND_EXCHANGE,
                        RabbitMQConfig.BEHAVIOR_ROUTING_KEY,
                        new UserBehaviorMessage(userId, item.getProductId(), 4));
            } catch (Exception ignore) {
                // 埋点发送失败不影响主流程
            }
        }
    }

    private void settleShopCustomer(Order order, Long userId) {
        if (order.getShopId() != null && order.getShopId() > 0) {
            shopCustomerMapper.insertOrUpdatePurchaseTime(order.getShopId(), userId);
        }
    }

    @Override
    @Transactional
    public void batchPay(List<Long> orderIds, Integer payType) {
        Long userId = requireUserId();
        if (orderIds == null || orderIds.isEmpty()) {
            throw new BusinessException("订单ID列表不能为空");
        }

        List<Order> orders = validateBatchOrders(orderIds, userId);
        BigDecimal totalPayAmount = orders.stream()
                .map(Order::getPayAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        int balRows = userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .ge(User::getBalance, totalPayAmount)
                .setSql("balance = balance - " + totalPayAmount.toPlainString()));
        if (balRows != 1) {
            throw new BusinessException("余额不足");
        }

        for (Order order : orders) {
            int paidRows = this.baseMapper.update(null, new LambdaUpdateWrapper<Order>()
                    .eq(Order::getId, order.getId())
                    .eq(Order::getStatus, 0)
                    .set(Order::getStatus, 1)
                    .set(Order::getPayType, payType != null ? payType : 1)
                    .set(Order::getPayTime, LocalDateTime.now(ZoneId.systemDefault())));
            if (paidRows != 1) {
                throw new BusinessException("订单状态不允许支付（id=" + order.getId() + "，可能已支付或已取消）");
            }
            saveStatusLog(order.getId(), 0, 1, userId, "USER", "批量支付");
            recordPurchaseBehavior(userId, order.getId());
            if (order.getShopId() != null && order.getShopId() > 0) {
                shopCustomerMapper.insertOrUpdatePurchaseTime(order.getShopId(), userId);
            }
        }
    }

    @Override
    @Transactional
    public void cancel(Long orderId, Long orderItemId, String reason) {
        Long userId = requireUserId();
        Order order = this.getById(orderId);
        if (order == null) throw new BusinessException(ResultCode.NOT_FOUND);
        if (!userId.equals(order.getUserId())) throw new BusinessException(ResultCode.FORBIDDEN);

        int from = order.getStatus();
        if (from != 0 && from != 1) {
            throw new BusinessException("当前状态不可取消（" + statusDesc(from) + "）");
        }

        if (orderItemId != null) {
            partialCancel(order, orderItemId, userId, from, reason);
        } else {
            fullCancel(order, userId, from, reason);
        }
    }

    private void fullCancel(Order order, Long userId, int from, String reason) {
        OrderStatus.checkTransition(from, -1);
        int rows = this.baseMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, order.getId())
                .eq(Order::getStatus, from)
                .set(Order::getStatus, -1)
                .set(Order::getCancelReason, reason));
        if (rows != 1) {
            throw new BusinessException("取消失败，订单状态已被并发修改");
        }
        order.setStatus(-1);
        order.setCancelReason(reason);
        saveStatusLog(order.getId(), from, -1, userId, "USER", reason);

        rollbackStock(order.getId());
        rollbackCoupon(order);
        if (from == 1) {
            User user = userMapper.selectById(userId);
            if (user != null) {
                user.setBalance(user.getBalance().add(order.getPayAmount()));
                userMapper.updateById(user);
            }
        }
    }

    private void partialCancel(Order order, Long orderItemId, Long userId, int from, String reason) {
        List<OrderItem> allItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));

        OrderItem target = allItems.stream()
                .filter(i -> i.getId().equals(orderItemId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("订单明细不存在"));

        if (target.getCancelStatus() != null && target.getCancelStatus() > 0) {
            throw new BusinessException("该商品已取消");
        }
        if (target.getRefundStatus() != null && target.getRefundStatus() > 0) {
            throw new BusinessException("该商品在退款流程中，无法取消");
        }

        target.setCancelStatus(1);
        orderItemMapper.updateById(target);
        rollbackSingleItemStock(target);

        if (from == 1) {
            BigDecimal refundAmt = target.getRealPayAmount() != null ? target.getRealPayAmount() : BigDecimal.ZERO;
            if (refundAmt.compareTo(BigDecimal.ZERO) > 0) {
                User user = userMapper.selectById(userId);
                if (user != null) {
                    user.setBalance(user.getBalance().add(refundAmt));
                    userMapper.updateById(user);
                }
            }
        }

        boolean allCancelled = allItems.stream().allMatch(i ->
                i.getId().equals(orderItemId)
                        || (i.getCancelStatus() != null && i.getCancelStatus() > 0));

        if (allCancelled) {
            OrderStatus.checkTransition(from, -1);
            this.baseMapper.update(null, new LambdaUpdateWrapper<Order>()
                    .eq(Order::getId, order.getId())
                    .eq(Order::getStatus, from)
                    .set(Order::getStatus, -1)
                    .set(Order::getCancelReason, reason));
            order.setStatus(-1);
            saveStatusLog(order.getId(), from, -1, userId, "USER", "全部商品已取消");
            rollbackCoupon(order);
        } else {
            BigDecimal itemPay = target.getRealPayAmount() != null ? target.getRealPayAmount() : BigDecimal.ZERO;
            BigDecimal itemSub = target.getSubtotal() != null ? target.getSubtotal() : BigDecimal.ZERO;
            order.setPayAmount(order.getPayAmount().subtract(itemPay));
            order.setTotalAmount(order.getTotalAmount().subtract(itemSub));
            order.setDiscountAmount(order.getTotalAmount().subtract(order.getPayAmount()));
            this.updateById(order);
            saveStatusLog(order.getId(), from, from, userId, "USER",
                    "部分取消：" + target.getProductName() + "（" + reason + "）");
        }
    }

    private void rollbackSingleItemStock(OrderItem oi) {
        if (oi.getSkuId() != null && oi.getSkuId() != 0) {
            productSkuMapper.update(null, new LambdaUpdateWrapper<ProductSku>()
                    .eq(ProductSku::getId, oi.getSkuId())
                    .setSql("stock = stock + " + oi.getQuantity()));
            productMapper.update(null, new LambdaUpdateWrapper<Product>()
                    .eq(Product::getId, oi.getProductId())
                    .setSql("stock = stock + " + oi.getQuantity()));
        } else {
            productMapper.update(null, new LambdaUpdateWrapper<Product>()
                    .eq(Product::getId, oi.getProductId())
                    .setSql("stock = stock + " + oi.getQuantity()));
        }
    }

    @Override
    @Transactional
    public void ship(Long orderId, String courierCompany, String trackingNumber) {
        Order order = this.getById(orderId);
        if (order == null) throw new BusinessException(ResultCode.NOT_FOUND);
        // 归属校验
        ownershipChecker.assertOrderOwned(orderId);

        int from = order.getStatus();
        OrderStatus.checkTransition(from, 2);
        order.setStatus(2);
        order.setShipTime(LocalDateTime.now(ZoneId.systemDefault()));
        if (courierCompany != null) order.setCourierCompany(courierCompany);
        if (trackingNumber != null) order.setTrackingNumber(trackingNumber);
        this.updateById(order);
        saveStatusLog(order.getId(), from, 2, UserContext.getUserId(),
                UserContext.getRole(), "商家发货");
    }

    @Override
    @Transactional
    public void receive(Long orderId) {
        Long userId = requireUserId();
        Order order = this.getById(orderId);
        if (order == null) throw new BusinessException(ResultCode.NOT_FOUND);
        if (!userId.equals(order.getUserId())) throw new BusinessException(ResultCode.FORBIDDEN);

        int from = order.getStatus();
        OrderStatus.checkTransition(from, 3);
        order.setStatus(3);
        order.setReceiveTime(LocalDateTime.now(ZoneId.systemDefault()));
        this.updateById(order);
        saveStatusLog(order.getId(), from, 3, userId, "USER", "用户确认收货");

        // 确认收货后赠送积分（= 实付金额向下取整，与结算页"预计赠送"口径一致），用于会员升级
        BigDecimal pay = order.getPayAmount() != null ? order.getPayAmount() : BigDecimal.ZERO;
        int gained = pay.setScale(0, RoundingMode.DOWN).intValue();
        if (gained > 0) {
            User user = userMapper.selectById(userId);
            if (user != null) {
                int current = user.getPoints() != null ? user.getPoints() : 0;
                user.setPoints(current + gained);
                userMapper.updateById(user);
            }
        }
    }

    @Override
    @Transactional
    public void deleteOrder(Long orderId) {
        Long userId = requireUserId();
        Order order = this.getById(orderId);
        if (order == null) throw new BusinessException(ResultCode.NOT_FOUND);
        if (!userId.equals(order.getUserId())) throw new BusinessException(ResultCode.FORBIDDEN);

        // 仅终态订单可删除：已取消、已完成、已退款
        if (order.getStatus() != -1 && order.getStatus() != 4
                && order.getStatus() != -3 && order.getStatus() != -4) {
            throw new BusinessException("仅已取消/已完成/已退款的订单可以删除");
        }

        // 先删明细（物理删，order_item 继承 BaseEntity 有 @TableLogic 软删）
        orderItemMapper.delete(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId));
        // 删状态日志
        orderStatusLogMapper.delete(new LambdaQueryWrapper<OrderStatusLog>()
                .eq(OrderStatusLog::getOrderId, orderId));
        // 删订单（软删）
        this.removeById(orderId);
    }

    @Override
    @Transactional
    public int cancelTimeoutOrders(int timeoutMinutes) {
        LocalDateTime deadline = LocalDateTime.now(ZoneId.systemDefault()).minusMinutes(timeoutMinutes);
        // 待支付(0) 且创建时间早于截止点的订单（MQ 漏网单的兜底扫描）
        List<Order> expired = this.list(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, 0)
                .le(Order::getCreateTime, deadline));
        int cancelled = 0;
        for (Order order : expired) {
            if (doTimeoutCancel(order)) cancelled++;
        }
        return cancelled;
    }

    @Override
    @Transactional
    public boolean cancelOneIfUnpaid(Long orderId) {
        if (orderId == null) return false;
        Order order = this.getById(orderId);
        if (order == null) return false;
        return doTimeoutCancel(order);
    }

    /**
     * 超时取消单笔订单：CAS 抢占 status 0→-1（避免与用户支付/手动取消并发），
     * 成功后回滚库存+优惠券（未支付无需退余额），记 SYSTEM 日志。
     * 跨店拆单时优惠券释放沿用 rollbackCoupon 的守卫（仅无其它未终结订单占用时才释放）。
     * @return 是否真正取消（false = 已被并发处理或非待支付）
     */
    private boolean doTimeoutCancel(Order order) {
        int rows = this.baseMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, order.getId())
                .eq(Order::getStatus, 0)
                .set(Order::getStatus, -1)
                .set(Order::getCancelReason, "超时未支付，系统自动取消"));
        if (rows != 1) return false;
        saveStatusLog(order.getId(), 0, -1, null, "SYSTEM", "超时未支付自动取消");
        rollbackStock(order.getId());
        rollbackCoupon(order);
        return true;
    }

    // ==================== 内部工具 ====================

    /** 将分页订单结果包装为含 orderItems 的 PageResult */
    private PageResult<Map<String, Object>> buildOrderPageResult(Page<Order> page) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (Order order : page.getRecords()) {
            Map<String, Object> vo = new LinkedHashMap<>();
            vo.put("id", order.getId());
            vo.put("orderNo", order.getOrderNo());
            vo.put("userId", order.getUserId());
            vo.put("shopId", order.getShopId());
            Shop shop = shopMapper.selectById(order.getShopId());
            vo.put("shopName", shop != null ? shop.getName() : "");
            vo.put(TOTAL_AMOUNT, order.getTotalAmount());
            vo.put(DISCOUNT_AMOUNT, order.getDiscountAmount());
            vo.put(PAY_AMOUNT, order.getPayAmount());
            vo.put(COUPON_ID, order.getCouponId());
            vo.put(STATUS, order.getStatus());
            vo.put("statusName", OrderStatus.of(order.getStatus()) != null
                    ? OrderStatus.of(order.getStatus()).getDesc() : "未知");
            vo.put("payType", order.getPayType());
            vo.put("payTime", order.getPayTime());
            vo.put("shipTime", order.getShipTime());
            vo.put("receiveTime", order.getReceiveTime());
            vo.put("cancelReason", order.getCancelReason());
            vo.put("receiverName", order.getReceiverName());
            vo.put("receiverPhone", order.getReceiverPhone());
            vo.put("receiverAddress", order.getReceiverAddress());
            vo.put("courierCompany", order.getCourierCompany());
            vo.put("trackingNumber", order.getTrackingNumber());
            vo.put("createTime", order.getCreateTime());

            // 查关联明细
            LambdaQueryWrapper<OrderItem> oiWrapper = new LambdaQueryWrapper<>();
            oiWrapper.eq(OrderItem::getOrderId, order.getId());
            vo.put("orderItems", orderItemMapper.selectList(oiWrapper));

            attachRefundInfo(vo, order);
            records.add(vo);
        }

        PageResult<Map<String, Object>> pr = new PageResult<>();
        pr.setTotal(page.getTotal());
        pr.setPages(page.getPages());
        pr.setCurrent(page.getCurrent());
        pr.setSize(page.getSize());
        pr.setRecords(records);
        return pr;
    }

    /** 构建单个订单的 Map 数据（含 orderItems 等）。 */
    private Map<String, Object> buildOrderMap(Order order) {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("id", order.getId());
        vo.put("orderNo", order.getOrderNo());
        vo.put("userId", order.getUserId());
        vo.put("shopId", order.getShopId());
        // 查店铺名称
        Shop shop = shopMapper.selectById(order.getShopId());
        vo.put("shopName", shop != null ? shop.getName() : "");
        vo.put(TOTAL_AMOUNT, order.getTotalAmount());
        vo.put(DISCOUNT_AMOUNT, order.getDiscountAmount());
        vo.put(PAY_AMOUNT, order.getPayAmount());
        vo.put(COUPON_ID, order.getCouponId());
        vo.put(STATUS, order.getStatus());
        vo.put("statusName", OrderStatus.of(order.getStatus()) != null
                ? OrderStatus.of(order.getStatus()).getDesc() : "未知");
        vo.put("payType", order.getPayType());
        vo.put("payTime", order.getPayTime());
        vo.put("shipTime", order.getShipTime());
        vo.put("receiveTime", order.getReceiveTime());
        vo.put("cancelReason", order.getCancelReason());
        vo.put("receiverName", order.getReceiverName());
        vo.put("receiverPhone", order.getReceiverPhone());
        vo.put("receiverAddress", order.getReceiverAddress());
        vo.put("courierCompany", order.getCourierCompany());
        vo.put("trackingNumber", order.getTrackingNumber());
        vo.put("createTime", order.getCreateTime());
        // 关联明细
        LambdaQueryWrapper<OrderItem> oiWrapper = new LambdaQueryWrapper<>();
        oiWrapper.eq(OrderItem::getOrderId, order.getId());
        vo.put("orderItems", orderItemMapper.selectList(oiWrapper));
        attachRefundInfo(vo, order);
        return vo;
    }

    /**
     * 退款中/已退款订单附带最新退单进度，供前端展示
     * "退款审核中 / 待寄回退货(填单号) / 退货已寄出待商家确认 / 已退款 / 已驳回"。
     * 驳回后订单已恢复原状态(2/3/4)，此时仍需带出被驳回的退款单，
     * 否则用户订单页看不到任何退款过的痕迹。
     */
    private void attachRefundInfo(Map<String, Object> vo, Order order) {
        if (order.getStatus() == null || order.getStatus() == 0
                || order.getStatus() == 1 || order.getStatus() == -1) return;
        // 查询所有活跃的退款单（包括被驳回的最新一条）
        List<Refund> refunds = refundMapper.selectList(new LambdaQueryWrapper<Refund>()
                .eq(Refund::getOrderId, order.getId())
                .orderByDesc(Refund::getCreateTime));
        if (refunds.isEmpty()) return;

        // 兼容：保留 refund 字段（最新一条活跃退款或被驳回退款）
        Refund latest = refunds.get(0);
        boolean orderInRefund = order.getStatus() <= -2;
        boolean hasActiveOrRejected = orderInRefund || latest.getStatus() == 2;
        // 部分退款：订单未冻结但有活跃退款单
        if (!hasActiveOrRejected) {
            hasActiveOrRejected = refunds.stream().anyMatch(r ->
                    r.getStatus() != null && (r.getStatus() == 0 || r.getStatus() == 3 || r.getStatus() == 4));
        }
        if (!hasActiveOrRejected) return;

        Map<String, Object> rf = buildRefundMap(latest);
        vo.put("refund", rf);

        // 附加所有活跃退款的列表（部分退款时前端需展示多条）
        List<Map<String, Object>> refundList = new ArrayList<>();
        for (Refund r : refunds) {
            if (r.getStatus() != null && r.getStatus() != 1 && r.getStatus() != 2) {
                refundList.add(buildRefundMap(r));
            }
        }
        // 也包含最近被驳回的
        for (Refund r : refunds) {
            if (r.getStatus() != null && r.getStatus() == 2) {
                refundList.add(buildRefundMap(r));
                break;
            }
        }
        if (!refundList.isEmpty()) {
            vo.put("refunds", refundList);
        }
    }

    private Map<String, Object> buildRefundMap(Refund refund) {
        Map<String, Object> rf = new LinkedHashMap<>();
        rf.put("id", refund.getId());
        rf.put("refundNo", refund.getRefundNo());
        rf.put("orderItemId", refund.getOrderItemId());
        rf.put(STATUS, refund.getStatus());
        rf.put("refundType", refund.getRefundType());
        rf.put("received", refund.getReceived());
        rf.put("amount", refund.getAmount());
        rf.put("reason", refund.getReason());
        rf.put("auditRemark", refund.getAuditRemark());
        rf.put("auditTime", refund.getAuditTime());
        rf.put("returnCourierCompany", refund.getReturnCourierCompany());
        rf.put("returnTrackingNumber", refund.getReturnTrackingNumber());
        rf.put("returnTime", refund.getReturnTime());
        return rf;
    }

    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();

    /** 生成订单号：ORD + yyyyMMdd + 时间戳后7位 + 3位随机数，避免 Redis 重启导致序号重复 */
    private String nextOrderNo(String datePrefix) {
        long timestampSuffix = System.currentTimeMillis() % 10000000L;
        int randomDigits = SECURE_RANDOM.nextInt(1000);
        return "ORD" + datePrefix + String.format("%07d", timestampSuffix) + String.format("%03d", randomDigits);
    }

    /** 校验商品存在且在架 */
    private Product getValidProduct(Long productId) {
        Product p = productMapper.selectById(productId);
        if (p == null || p.getStatus() == null || p.getStatus() != 1) {
            throw new BusinessException("商品不存在或已下架（id=" + productId + "）");
        }
        return p;
    }

    /** 校验 SKU 存在且归属正确 */
    private ProductSku getValidSku(Long skuId, Long productId) {
        ProductSku sku = productSkuMapper.selectById(skuId);
        if (sku == null || !sku.getProductId().equals(productId)) {
            throw new BusinessException("商品规格不存在");
        }
        return sku;
    }

    private Long validateAddressId(Long addressId, Long userId) {
        if (addressId == null || addressId <= 0) {
            throw new BusinessException("请选择收货地址");
        }
        Address addr = addressMapper.selectById(addressId);
        if (addr == null || !addr.getUserId().equals(userId)) {
            throw new BusinessException("收货地址不存在");
        }
        return addressId;
    }

    private Long requireUserId() {
        Long uid = UserContext.getUserId();
        if (uid == null) throw new BusinessException(ResultCode.UNAUTHORIZED);
        return uid;
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        return Long.parseLong(v.toString());
    }

    private int toInt(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return def; }
    }

    /** 记录订单状态流转日志 */
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

    /** 回滚库存：将该订单所有 order_item 的库存恢复 */
    private void rollbackStock(Long orderId) {
        LambdaQueryWrapper<OrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderItem::getOrderId, orderId);
        List<OrderItem> items = orderItemMapper.selectList(wrapper);
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

    /**
     * 回滚优惠券：仅当该券关联的其它订单都已终结（已取消/已退款）时才释放。
     * 跨店拆单时同一张券会挂在多个子订单上，取消其中一单不能放飞整张券。
     */
    private void rollbackCoupon(Order order) {
        if (order.getCouponId() == null || order.getCouponId() <= 0) return;
        // 当前单此时已被置为终态；统计该券是否还被别的未终结订单占用
        long stillInUse = this.count(new LambdaQueryWrapper<Order>()
                .eq(Order::getCouponId, order.getCouponId())
                .ne(Order::getId, order.getId())
                .notIn(Order::getStatus, -1, -3, -4));
        if (stillInUse > 0) return;
        userCouponMapper.update(null, new LambdaUpdateWrapper<UserCoupon>()
                .eq(UserCoupon::getId, order.getCouponId())
                .set(UserCoupon::getStatus, 0)
                .set(UserCoupon::getUsedTime, null)
                .set(UserCoupon::getOrderId, 0L));
    }

    // ---------- 复杂度拆分辅助方法 ----------

    private BigDecimal calculateItemsTotal(List<Map<String, Object>> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> item : items) {
            Long productId = toLong(item.get("productId"));
            Long skuId = toLong(item.get("skuId"));
            int qty = toInt(item.get("quantity"), 1);
            Product product = getValidProduct(productId);
            BigDecimal price;
            if (skuId != null && skuId != 0) {
                ProductSku sku = getValidSku(skuId, productId);
                price = sku.getPrice();
            } else {
                price = product.getPrice();
            }
            if (price == null) {
                throw new BusinessException("商品价格异常");
            }
            total = total.add(price.multiply(BigDecimal.valueOf(qty)));
        }
        return total;
    }

    private Coupon validateUserCoupon(Long userCouponId, Long userId, BigDecimal totalAmount) {
        if (userCouponId == null || userCouponId <= 0) return null;
        UserCoupon userCoupon = checkUserCouponOwnership(userCouponId, userId);
        Coupon coupon = couponMapper.selectById(userCoupon.getCouponId());
        if (!isCouponUsable(coupon, totalAmount)) return null;
        return coupon;
    }

    private UserCoupon checkUserCouponOwnership(Long userCouponId, Long userId) {
        UserCoupon userCoupon = userCouponMapper.selectById(userCouponId);
        if (userCoupon == null || !userCoupon.getUserId().equals(userId)) {
            throw new BusinessException("优惠券不存在");
        }
        if (userCoupon.getStatus() != null && userCoupon.getStatus() != 0) {
            throw new BusinessException("优惠券不可用");
        }
        return userCoupon;
    }

    private boolean isCouponUsable(Coupon coupon, BigDecimal totalAmount) {
        if (coupon == null || coupon.getStatus() != 1) return false;
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        if (now.isBefore(coupon.getStartTime()) || now.isAfter(coupon.getEndTime())) return false;
        return totalAmount.compareTo(coupon.getThreshold()) >= 0;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> checkIdempotency(String requestId) {
        if (requestId == null || requestId.isEmpty()) return null;
        Object cached = redisUtil.get(REDIS_REQUEST_ID_PREFIX + requestId);
        if (cached == null) return null;
        try {
            return OBJECT_MAPPER.readValue(cached.toString(), LinkedHashMap.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException("订单幂等缓存异常");
        }
    }

    private List<ItemLine> buildItemLines(List<Map<String, Object>> items) {
        List<ItemLine> itemLines = new ArrayList<>();
        for (Map<String, Object> item : items) {
            itemLines.add(resolveItemLine(item));
        }
        return itemLines;
    }

    private ItemLine resolveItemLine(Map<String, Object> item) {
        Long productId = toLong(item.get("productId"));
        Long skuId = toLong(item.get("skuId"));
        int qty = toInt(item.get("quantity"), 1);
        if (qty <= 0) throw new BusinessException("购买数量必须大于0");

        Product product = getValidProduct(productId);
        if (product.getShopId() == null) throw new BusinessException("商品未绑定店铺");

        BigDecimal price;
        int availableStock;
        String specName = "";
        if (skuId != null && skuId != 0) {
            ProductSku sku = getValidSku(skuId, productId);
            price = sku.getPrice();
            availableStock = sku.getStock() == null ? 0 : sku.getStock();
            specName = sku.getSpecName() != null ? sku.getSpecName() : "";
        } else {
            price = product.getPrice();
            availableStock = product.getStock() == null ? 0 : product.getStock();
        }
        if (price == null) throw new BusinessException("商品价格异常");
        if (availableStock < qty) {
            throw new BusinessException(product.getName() + " 库存不足（剩余 " + availableStock + "）");
        }
        return new ItemLine(product, skuId, price, qty, product.getShopId(), specName);
    }

    private CouponLockResult lockCoupon(Long userCouponId, Long userId,
                                         BigDecimal totalOrderAmount, BigDecimal amountAfterMember) {
        if (userCouponId == null || userCouponId <= 0) {
            return new CouponLockResult(BigDecimal.ZERO, null);
        }
        UserCoupon userCoupon = validateUserCoupon(userCouponId, userId);
        Coupon coupon = validateCouponPeriod(userCoupon.getCouponId());
        if (totalOrderAmount.compareTo(coupon.getThreshold()) < 0) {
            throw new BusinessException("未达到优惠券门槛（满 " + coupon.getThreshold() + " 可用）");
        }
        BigDecimal discount = calcDiscount(coupon, amountAfterMember);
        casMarkCouponUsed(userCouponId);
        return new CouponLockResult(discount, userCoupon);
    }

    private UserCoupon validateUserCoupon(Long userCouponId, Long userId) {
        UserCoupon uc = userCouponMapper.selectById(userCouponId);
        if (uc == null || !uc.getUserId().equals(userId)) throw new BusinessException("优惠券不存在");
        if (uc.getStatus() != null && uc.getStatus() != 0) throw new BusinessException("优惠券已使用或已过期");
        return uc;
    }

    private Coupon validateCouponPeriod(Long couponId) {
        Coupon c = couponMapper.selectById(couponId);
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        if (c == null || c.getStatus() != 1 || now.isBefore(c.getStartTime()) || now.isAfter(c.getEndTime())) {
            throw new BusinessException("优惠券不在有效期");
        }
        return c;
    }

    private BigDecimal calcDiscount(Coupon coupon, BigDecimal amountAfterMember) {
        if (coupon.getType() == 1) return coupon.getAmount();
        if (coupon.getType() == 2) return amountAfterMember.multiply(BigDecimal.ONE.subtract(coupon.getAmount()));
        return BigDecimal.ZERO;
    }

    private void casMarkCouponUsed(Long userCouponId) {
        int rows = userCouponMapper.update(null, new LambdaUpdateWrapper<UserCoupon>()
                .eq(UserCoupon::getId, userCouponId)
                .eq(UserCoupon::getStatus, 0)
                .set(UserCoupon::getStatus, 1)
                .set(UserCoupon::getUsedTime, LocalDateTime.now(ZoneId.systemDefault())));
        if (rows != 1) throw new BusinessException("优惠券已被使用");
    }

    private void createShopSubOrder(Long shopId, List<ItemLine> lines, BigDecimal totalOrderAmount,
                                     BigDecimal totalPayAmount, Long userCouponId, UserCoupon usedUserCoupon,
                                     Address address, String remark, Long userId, String today,
                                     List<Long> orderIds, List<String> orderNos) {
        BigDecimal subTotalAmount = lines.stream()
                .map(line -> line.price.multiply(BigDecimal.valueOf(line.qty)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal ratio = totalOrderAmount.compareTo(BigDecimal.ZERO) > 0
                ? subTotalAmount.divide(totalOrderAmount, 6, RoundingMode.HALF_UP)
                : BigDecimal.ONE;
        BigDecimal subPayAmount = totalPayAmount.multiply(ratio).setScale(2, RoundingMode.HALF_UP);

        String orderNo = nextOrderNo(today);

        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setShopId(shopId);
        order.setTotalAmount(subTotalAmount);
        order.setDiscountAmount(subTotalAmount.subtract(subPayAmount));
        order.setPayAmount(subPayAmount);
        order.setCouponId(userCouponId != null ? userCouponId : 0L);
        order.setStatus(0);
        order.setReceiverName(address.getReceiver());
        order.setReceiverPhone(address.getPhone());
        order.setReceiverAddress(address.getProvince() + address.getCity()
                + address.getDistrict() + address.getDetail());

        if (remark != null && !remark.isEmpty()) {
            order.setRemark(remark);
        }
        this.save(order);
        orderIds.add(order.getId());
        orderNos.add(orderNo);

        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_DELAY_EXCHANGE,
                    RabbitMQConfig.ORDER_DELAY_ROUTING_KEY, String.valueOf(order.getId()));
        } catch (Exception ignore) {
            // MQ 不可用不影响下单，超时由定时扫描兜底
        }

        saveStatusLog(order.getId(), null, 0, userId, "USER", "用户下单");

        for (ItemLine line : lines) {
            processOrderItem(line, order, subPayAmount, subTotalAmount, shopId, userId);
        }

        if (usedUserCoupon != null) {
            userCouponMapper.update(null, new LambdaUpdateWrapper<UserCoupon>()
                    .eq(UserCoupon::getId, usedUserCoupon.getId())
                    .set(UserCoupon::getOrderId, order.getId()));
        }
    }

    private void processOrderItem(ItemLine line, Order order, BigDecimal subPayAmount,
                                   BigDecimal subTotalAmount, Long shopId, Long userId) {
        BigDecimal lineSubtotal = line.price.multiply(BigDecimal.valueOf(line.qty));
        BigDecimal lineRatio = subTotalAmount.compareTo(BigDecimal.ZERO) > 0
                ? lineSubtotal.divide(subTotalAmount, 6, RoundingMode.HALF_UP)
                : BigDecimal.ONE;
        BigDecimal lineRealPay = subPayAmount.multiply(lineRatio).setScale(2, RoundingMode.HALF_UP);

        OrderItem orderItem = new OrderItem();
        orderItem.setOrderId(order.getId());
        orderItem.setProductId(line.product.getId());
        orderItem.setSkuId(line.skuId != null ? line.skuId : 0L);
        orderItem.setShopId(shopId);
        orderItem.setProductName(line.product.getName());
        orderItem.setProductImage(line.product.getMainImage());
        orderItem.setSpecName(line.specName);
        orderItem.setPrice(line.price);
        orderItem.setQuantity(line.qty);
        orderItem.setSubtotal(lineSubtotal);
        orderItem.setRealPayAmount(lineRealPay);
        orderItemMapper.insert(orderItem);

        int stockRows;
        if (line.skuId != null && line.skuId != 0) {
            stockRows = productSkuMapper.update(null, new LambdaUpdateWrapper<ProductSku>()
                    .eq(ProductSku::getId, line.skuId)
                    .ge(ProductSku::getStock, line.qty)
                    .setSql("stock = stock - " + line.qty));
            if (stockRows == 1) {
                // 同步扣减主表 stock（主表 stock = 所有 SKU 库存之和）
                productMapper.update(null, new LambdaUpdateWrapper<Product>()
                        .eq(Product::getId, line.product.getId())
                        .setSql("stock = stock - " + line.qty));
            }
        } else {
            stockRows = productMapper.update(null, new LambdaUpdateWrapper<Product>()
                    .eq(Product::getId, line.product.getId())
                    .ge(Product::getStock, line.qty)
                    .setSql("stock = stock - " + line.qty));
        }
        if (stockRows != 1) {
            throw new BusinessException(line.product.getName() + " 库存不足，请重试");
        }

        Long targetSkuId = (line.skuId != null && line.skuId != 0) ? line.skuId : 0L;
        cartItemMapper.delete(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getProductId, line.product.getId())
                .eq(CartItem::getSkuId, targetSkuId));
    }

    private void cacheIdempotentResult(String requestId, Map<String, Object> result) {
        if (requestId == null || requestId.isEmpty()) return;
        try {
            redisUtil.set(REDIS_REQUEST_ID_PREFIX + requestId,
                    OBJECT_MAPPER.writeValueAsString(result),
                    REQUEST_ID_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            // 幂等缓存失败不影响下单
        }
    }

    private List<Order> validateBatchOrders(List<Long> orderIds, Long userId) {
        List<Order> orders = new ArrayList<>();
        for (Long orderId : orderIds) {
            Order order = this.getById(orderId);
            if (order == null) throw new BusinessException("订单不存在（id=" + orderId + "）");
            if (!userId.equals(order.getUserId())) throw new BusinessException("无权支付该订单");
            if (order.getStatus() == null || order.getStatus() != 0) {
                throw new BusinessException("订单状态不允许支付（id=" + orderId + "）");
            }
            orders.add(order);
        }
        return orders;
    }

    // ---------- 内部 DTO ----------

    private static class CouponLockResult {
        final BigDecimal discount;
        final UserCoupon usedUserCoupon;
        CouponLockResult(BigDecimal discount, UserCoupon usedUserCoupon) {
            this.discount = discount;
            this.usedUserCoupon = usedUserCoupon;
        }
    }

    private static class ItemLine {
        final Product product;
        final Long skuId;
        final BigDecimal price;
        final int qty;
        final Long shopId;
        final String specName;

        ItemLine(Product product, Long skuId, BigDecimal price, int qty, Long shopId, String specName) {
            this.product = product;
            this.skuId = skuId;
            this.price = price;
            this.qty = qty;
            this.shopId = shopId;
            this.specName = specName;
        }

        Long getShopId() { return shopId; }
    }
}
