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
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 订单 Service 实现——整个 C 模块最核心的类。
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

        // 1) 逐项校验商品与规格，计算总金额
        BigDecimal totalAmount = BigDecimal.ZERO;
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
            totalAmount = totalAmount.add(price.multiply(BigDecimal.valueOf(qty)));
        }

        // 2) 会员折扣试算
        java.math.BigDecimal userDiscountRate = membershipLevelService.getCurrentUserDiscount();
        BigDecimal memberDiscount = totalAmount.multiply(BigDecimal.ONE.subtract(userDiscountRate));
        BigDecimal amountAfterMember = totalAmount.subtract(memberDiscount);

        // 3) 优惠券折扣试算
        BigDecimal couponDiscount = BigDecimal.ZERO;
        Long effectiveUserCouponId = 0L;
        if (userCouponId != null && userCouponId > 0) {
            UserCoupon uc = userCouponMapper.selectById(userCouponId);
            if (uc == null || !uc.getUserId().equals(userId)) {
                throw new BusinessException("优惠券不存在");
            }
            if (uc.getStatus() != null && uc.getStatus() != 0) {
                throw new BusinessException("优惠券不可用");
            }
            Coupon coupon = couponMapper.selectById(uc.getCouponId());
            if (coupon != null && coupon.getStatus() == 1
                    && !LocalDateTime.now().isBefore(coupon.getStartTime())
                    && !LocalDateTime.now().isAfter(coupon.getEndTime())) {
                // 门槛按商品原价总额判定（与前端 couponUsable 及"满X元"惯例一致），折扣仍按会员折后价计算
                if (totalAmount.compareTo(coupon.getThreshold()) >= 0) {
                    if (coupon.getType() == 1) {
                        // 满减
                        couponDiscount = coupon.getAmount();
                    } else if (coupon.getType() == 2) {
                        // 折扣：amount = 0.9 表示 9 折
                        couponDiscount = amountAfterMember.multiply(
                                BigDecimal.ONE.subtract(coupon.getAmount()));
                    }
                    effectiveUserCouponId = userCouponId;
                }
            }
        }

        // memberDiscount 已在上面计算

        // 4) 组装响应
        BigDecimal discountAmount = memberDiscount.add(couponDiscount);
        BigDecimal payAmount = totalAmount.subtract(discountAmount);
        if (payAmount.compareTo(BigDecimal.ZERO) < 0) payAmount = BigDecimal.ZERO;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalAmount", totalAmount);
        result.put("discountAmount", discountAmount);
        result.put("memberDiscount", memberDiscount);
        result.put("couponDiscount", couponDiscount);
        result.put("payAmount", payAmount);
        result.put("couponId", effectiveUserCouponId);
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

        // 0) 幂等防重
        if (requestId != null && !requestId.isEmpty()) {
            String redisKey = REDIS_REQUEST_ID_PREFIX + requestId;
            Object cached = redisUtil.get(redisKey);
            if (cached != null) {
                try {
                    return OBJECT_MAPPER.readValue(cached.toString(), LinkedHashMap.class);
                } catch (JsonProcessingException e) {
                    throw new BusinessException("订单幂等缓存异常");
                }
            }
        }

        // 1) 校验收货地址
        Long addrId = validateAddressId(addressId, userId);
        Address addr = addressMapper.selectById(addrId);

        // 2) 逐项校验商品、SKU、库存，同时按 shop_id 分组
        List<ItemLine> itemLines = new ArrayList<>();
        for (Map<String, Object> item : items) {
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

            itemLines.add(new ItemLine(product, skuId, price, qty, product.getShopId(), specName));
        }

        // 3) 按 shopId 分组（跨店拆单）
        Map<Long, List<ItemLine>> grouped = itemLines.stream()
                .collect(Collectors.groupingBy(ItemLine::getShopId, LinkedHashMap::new, Collectors.toList()));

        // 4) 总金额
        BigDecimal totalOrderAmount = itemLines.stream()
                .map(l -> l.price.multiply(BigDecimal.valueOf(l.qty)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 4.5) 会员折扣试算
        java.math.BigDecimal userDiscountRate = membershipLevelService.getCurrentUserDiscount();
        BigDecimal totalMemberDiscount = totalOrderAmount.multiply(BigDecimal.ONE.subtract(userDiscountRate));
        BigDecimal amountAfterMember = totalOrderAmount.subtract(totalMemberDiscount);

        // 5) 优惠券 CAS 锁定
        BigDecimal totalCouponDiscount = BigDecimal.ZERO;
        UserCoupon usedUc = null;
        if (userCouponId != null && userCouponId > 0) {
            UserCoupon uc = userCouponMapper.selectById(userCouponId);
            if (uc == null || !uc.getUserId().equals(userId)) throw new BusinessException("优惠券不存在");
            if (uc.getStatus() != null && uc.getStatus() != 0) throw new BusinessException("优惠券已使用或已过期");
            Coupon coupon = couponMapper.selectById(uc.getCouponId());
            if (coupon == null || coupon.getStatus() != 1
                    || LocalDateTime.now().isBefore(coupon.getStartTime())
                    || LocalDateTime.now().isAfter(coupon.getEndTime())) {
                throw new BusinessException("优惠券不在有效期");
            }
            // 门槛按商品原价总额判定（与前端及"满X元"惯例一致），折扣仍按会员折后价计算
            if (totalOrderAmount.compareTo(coupon.getThreshold()) < 0) {
                throw new BusinessException("未达到优惠券门槛（满 " + coupon.getThreshold() + " 可用）");
            }
            if (coupon.getType() == 1) {
                totalCouponDiscount = coupon.getAmount();
            } else if (coupon.getType() == 2) {
                totalCouponDiscount = amountAfterMember.multiply(BigDecimal.ONE.subtract(coupon.getAmount()));
            }

            // CAS 乐观锁扣券
            int rows = userCouponMapper.update(null, new LambdaUpdateWrapper<UserCoupon>()
                    .eq(UserCoupon::getId, userCouponId)
                    .eq(UserCoupon::getStatus, 0)
                    .set(UserCoupon::getStatus, 1)
                    .set(UserCoupon::getUsedTime, LocalDateTime.now()));
            if (rows != 1) {
                throw new BusinessException("优惠券已被使用");
            }
            usedUc = uc;
        }

        // 6) 逐店创建订单
        BigDecimal totalPayAmount = totalOrderAmount.subtract(totalCouponDiscount).subtract(totalMemberDiscount);
        if (totalPayAmount.compareTo(BigDecimal.ZERO) < 0) totalPayAmount = BigDecimal.ZERO;

        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        List<Long> orderIds = new ArrayList<>();
        List<String> orderNos = new ArrayList<>();

        for (Map.Entry<Long, List<ItemLine>> entry : grouped.entrySet()) {
            Long shopId = entry.getKey();
            List<ItemLine> lines = entry.getValue();

            // 该子订单商品总价
            BigDecimal subTotalAmount = lines.stream()
                    .map(l -> l.price.multiply(BigDecimal.valueOf(l.qty)))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // 按比例分配子订单实付金额
            BigDecimal ratio = totalOrderAmount.compareTo(BigDecimal.ZERO) > 0
                    ? subTotalAmount.divide(totalOrderAmount, 6, RoundingMode.HALF_UP)
                    : BigDecimal.ONE;
            BigDecimal subPayAmount = totalPayAmount.multiply(ratio).setScale(2, RoundingMode.HALF_UP);

            // 生成订单号
            String orderNo = nextOrderNo(today);

            Order order = new Order();
            order.setOrderNo(orderNo);
            order.setUserId(userId);
            order.setShopId(shopId);
            order.setTotalAmount(subTotalAmount);
            order.setDiscountAmount(subTotalAmount.subtract(subPayAmount));
            order.setPayAmount(subPayAmount);
            order.setCouponId(userCouponId != null ? userCouponId : 0L);
            order.setStatus(0); // 待支付
            order.setReceiverName(addr.getReceiver());
            order.setReceiverPhone(addr.getPhone());
            order.setReceiverAddress(addr.getProvince() + addr.getCity()
                    + addr.getDistrict() + addr.getDetail());

            if (remark != null && !remark.isEmpty()) {
                order.setRemark(remark);
            }
            this.save(order);
            orderIds.add(order.getId());
            orderNos.add(orderNo);

            // 发延迟消息：到期(默认30min)后若仍未支付则自动取消（MQ 主触发；定时扫描兜底）
            try {
                rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_DELAY_EXCHANGE,
                        RabbitMQConfig.ORDER_DELAY_ROUTING_KEY, String.valueOf(order.getId()));
            } catch (Exception ignore) {
                // MQ 不可用不影响下单，超时由定时扫描兜底
            }

            // 状态日志
            OrderStatusLog log = new OrderStatusLog();
            log.setOrderId(order.getId());
            log.setToStatus(0);
            log.setOperatorId(userId);
            log.setOperatorRole("USER");
            log.setRemark("用户下单");
            orderStatusLogMapper.insert(log);

            // 明细 + 优惠分摊 + 库存扣减
            for (ItemLine line : lines) {
                BigDecimal lineSubtotal = line.price.multiply(BigDecimal.valueOf(line.qty));
                BigDecimal lineRatio = subTotalAmount.compareTo(BigDecimal.ZERO) > 0
                        ? lineSubtotal.divide(subTotalAmount, 6, RoundingMode.HALF_UP)
                        : BigDecimal.ONE;
                BigDecimal lineRealPay = subPayAmount.multiply(lineRatio).setScale(2, RoundingMode.HALF_UP);

                OrderItem oi = new OrderItem();
                oi.setOrderId(order.getId());
                oi.setProductId(line.product.getId());
                oi.setSkuId(line.skuId != null ? line.skuId : 0L);
                oi.setShopId(shopId);
                oi.setProductName(line.product.getName());
                oi.setProductImage(line.product.getMainImage());
                oi.setSpecName(line.specName);
                oi.setPrice(line.price);
                oi.setQuantity(line.qty);
                oi.setSubtotal(lineSubtotal);
                oi.setRealPayAmount(lineRealPay);
                orderItemMapper.insert(oi);

                // 库存扣减（原子：WHERE stock>=qty，影响行数!=1 说明并发下已被抢空，抛错回滚整单，防超卖）
                int stockRows;
                if (line.skuId != null && line.skuId != 0) {
                    stockRows = productSkuMapper.update(null, new LambdaUpdateWrapper<ProductSku>()
                            .eq(ProductSku::getId, line.skuId)
                            .ge(ProductSku::getStock, line.qty)
                            .setSql("stock = stock - " + line.qty));
                } else {
                    stockRows = productMapper.update(null, new LambdaUpdateWrapper<Product>()
                            .eq(Product::getId, line.product.getId())
                            .ge(Product::getStock, line.qty)
                            .setSql("stock = stock - " + line.qty));
                }
                if (stockRows != 1) {
                    throw new BusinessException(line.product.getName() + " 库存不足，请重试");
                }

                // 清除购物车中已下单的商品（按 userId + productId + skuId 匹配）
                Long targetSkuId = (line.skuId != null && line.skuId != 0) ? line.skuId : 0L;
                cartItemMapper.delete(new LambdaQueryWrapper<CartItem>()
                        .eq(CartItem::getUserId, userId)
                        .eq(CartItem::getProductId, line.product.getId())
                        .eq(CartItem::getSkuId, targetSkuId));
            }

            // 回填 user_coupon 的 orderId
            if (usedUc != null) {
                userCouponMapper.update(null, new LambdaUpdateWrapper<UserCoupon>()
                        .eq(UserCoupon::getId, usedUc.getId())
                        .set(UserCoupon::getOrderId, order.getId()));
            }
        }

        // 7) 幂等缓存
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderIds", orderIds);
        result.put("orderNos", orderNos);
        result.put("totalPayAmount", totalPayAmount);
        result.put("requestId", requestId);

        if (requestId != null && !requestId.isEmpty()) {
            try {
                redisUtil.set(REDIS_REQUEST_ID_PREFIX + requestId,
                        OBJECT_MAPPER.writeValueAsString(result),
                        REQUEST_ID_TTL_MINUTES, TimeUnit.MINUTES);
            } catch (JsonProcessingException e) {
                // 幂等缓存失败不影响下单
            }
        }

        return result;
    }

    // ==================== 我的订单 ====================

    @Override
    public PageResult<Map<String, Object>> myOrders(int current, int size, Integer status) {
        Long userId = requireUserId();
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getUserId, userId);
        if (status != null) {
            wrapper.eq(Order::getStatus, status);
        }
        wrapper.orderByDesc(Order::getCreateTime);
        Page<Order> page = this.page(new Page<>(current, size), wrapper);
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
                .set(Order::getPayTime, LocalDateTime.now()));
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

        // 状态日志
        saveStatusLog(order.getId(), 0, 1, userId, "USER", "用户支付");

        // 记录购买行为埋点 (4购买)
        LambdaQueryWrapper<OrderItem> oiWrapper = new LambdaQueryWrapper<>();
        oiWrapper.eq(OrderItem::getOrderId, orderId);
        List<OrderItem> items = orderItemMapper.selectList(oiWrapper);
        for (OrderItem item : items) {
            UserBehavior behavior = new UserBehavior();
            behavior.setUserId(userId);
            behavior.setProductId(item.getProductId());
            behavior.setBehaviorType(4);
            userBehaviorMapper.insert(behavior);
            // 购买行为同步发 MQ，更新用户实时标签画像；MQ 不可用不应影响支付
            try {
                rabbitTemplate.convertAndSend(RabbitMQConfig.RECOMMEND_EXCHANGE,
                        RabbitMQConfig.BEHAVIOR_ROUTING_KEY,
                        new UserBehaviorMessage(userId, item.getProductId(), 4));
            } catch (Exception ignore) {
                // 忽略：埋点发送失败不影响主流程
            }
        }

        // 沉淀店铺客户关系
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

        // 1) 查询所有待支付订单并校验
        List<Order> orders = new ArrayList<>();
        BigDecimal totalPayAmount = BigDecimal.ZERO;
        for (Long orderId : orderIds) {
            Order order = this.getById(orderId);
            if (order == null) throw new BusinessException("订单不存在（id=" + orderId + "）");
            if (!userId.equals(order.getUserId())) throw new BusinessException("无权支付该订单");
            if (order.getStatus() == null || order.getStatus() != 0) {
                throw new BusinessException("订单状态不允许支付（id=" + orderId + "）");
            }
            orders.add(order);
            totalPayAmount = totalPayAmount.add(order.getPayAmount());
        }

        // 2) CAS 扣余额（一次性扣除总金额，WHERE balance>=total 原子判断，失败回滚整批）
        int balRows = userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .ge(User::getBalance, totalPayAmount)
                .setSql("balance = balance - " + totalPayAmount.toPlainString()));
        if (balRows != 1) {
            throw new BusinessException("余额不足");
        }

        // 3) 批量 CAS 更新订单状态（任一单已被并发支付/取消则整批回滚）
        for (Order order : orders) {
            int paidRows = this.baseMapper.update(null, new LambdaUpdateWrapper<Order>()
                    .eq(Order::getId, order.getId())
                    .eq(Order::getStatus, 0)
                    .set(Order::getStatus, 1)
                    .set(Order::getPayType, payType != null ? payType : 1)
                    .set(Order::getPayTime, LocalDateTime.now()));
            if (paidRows != 1) {
                throw new BusinessException("订单状态不允许支付（id=" + order.getId() + "，可能已支付或已取消）");
            }
            saveStatusLog(order.getId(), 0, 1, userId, "USER", "批量支付");

            // 记录购买行为埋点
            LambdaQueryWrapper<OrderItem> oiWrapper = new LambdaQueryWrapper<>();
            oiWrapper.eq(OrderItem::getOrderId, order.getId());
            List<OrderItem> items = orderItemMapper.selectList(oiWrapper);
            for (OrderItem item : items) {
                UserBehavior behavior = new UserBehavior();
                behavior.setUserId(userId);
                behavior.setProductId(item.getProductId());
                behavior.setBehaviorType(4);
                userBehaviorMapper.insert(behavior);
                // 购买行为同步发 MQ，更新用户实时标签画像；MQ 不可用不应影响支付
                try {
                    rabbitTemplate.convertAndSend(RabbitMQConfig.RECOMMEND_EXCHANGE,
                            RabbitMQConfig.BEHAVIOR_ROUTING_KEY,
                            new UserBehaviorMessage(userId, item.getProductId(), 4));
                } catch (Exception ignore) {
                    // 忽略：埋点发送失败不影响主流程
                }
            }

            // 沉淀店铺客户关系
            if (order.getShopId() != null && order.getShopId() > 0) {
                shopCustomerMapper.insertOrUpdatePurchaseTime(order.getShopId(), userId);
            }
        }
    }

    @Override
    @Transactional
    public void cancel(Long orderId, String reason) {
        Long userId = requireUserId();
        Order order = this.getById(orderId);
        if (order == null) throw new BusinessException(ResultCode.NOT_FOUND);
        if (!userId.equals(order.getUserId())) throw new BusinessException(ResultCode.FORBIDDEN);

        int from = order.getStatus();
        if (from != 0 && from != 1) {
            throw new BusinessException("当前状态不可取消（" + statusDesc(from) + "）");
        }

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

        // 回滚库存
        rollbackStock(order.getId());
        // 回滚优惠券
        rollbackCoupon(order);
        // 回滚余额（仅已支付订单）
        if (from == 1) {
            User user = userMapper.selectById(userId);
            if (user != null) {
                user.setBalance(user.getBalance().add(order.getPayAmount()));
                userMapper.updateById(user);
            }
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
        order.setShipTime(LocalDateTime.now());
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
        order.setReceiveTime(LocalDateTime.now());
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
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(timeoutMinutes);
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
            vo.put("totalAmount", order.getTotalAmount());
            vo.put("discountAmount", order.getDiscountAmount());
            vo.put("payAmount", order.getPayAmount());
            vo.put("couponId", order.getCouponId());
            vo.put("status", order.getStatus());
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
        vo.put("totalAmount", order.getTotalAmount());
        vo.put("discountAmount", order.getDiscountAmount());
        vo.put("payAmount", order.getPayAmount());
        vo.put("couponId", order.getCouponId());
        vo.put("status", order.getStatus());
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
     */
    private void attachRefundInfo(Map<String, Object> vo, Order order) {
        if (order.getStatus() == null || order.getStatus() > -2) return;
        Refund refund = refundMapper.selectOne(new LambdaQueryWrapper<Refund>()
                .eq(Refund::getOrderId, order.getId())
                .orderByDesc(Refund::getCreateTime)
                .last("LIMIT 1"));
        if (refund == null) return;
        Map<String, Object> rf = new LinkedHashMap<>();
        rf.put("id", refund.getId());
        rf.put("refundNo", refund.getRefundNo());
        rf.put("status", refund.getStatus());
        rf.put("refundType", refund.getRefundType());
        rf.put("received", refund.getReceived());
        rf.put("amount", refund.getAmount());
        rf.put("reason", refund.getReason());
        rf.put("auditRemark", refund.getAuditRemark());
        rf.put("returnCourierCompany", refund.getReturnCourierCompany());
        rf.put("returnTrackingNumber", refund.getReturnTrackingNumber());
        vo.put("refund", rf);
    }

    /** 生成订单号：ORD + yyyyMMdd + 时间戳后7位 + 3位随机数，避免 Redis 重启导致序号重复 */
    private String nextOrderNo(String datePrefix) {
        long timestampSuffix = System.currentTimeMillis() % 10000000L;
        int randomDigits = java.util.concurrent.ThreadLocalRandom.current().nextInt(1000);
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

    // ---------- 内部 DTO ----------

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
