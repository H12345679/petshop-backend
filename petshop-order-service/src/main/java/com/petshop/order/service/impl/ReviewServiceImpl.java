package com.petshop.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.petshop.common.BusinessException;
import com.petshop.common.OrderStatus;
import com.petshop.common.PageResult;
import com.petshop.common.ResultCode;
import com.petshop.order.entity.*;
import com.petshop.order.mapper.*;
import com.petshop.order.service.ReviewService;
import com.petshop.product.entity.Product;
import com.petshop.product.mapper.ProductMapper;
import com.petshop.security.OwnershipChecker;
import com.petshop.security.UserContext;
import com.petshop.user.entity.User;
import com.petshop.user.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 评价 Service 实现。
 * <p>
 * 关键逻辑：同一 order_item 只能评价一次（uk_item）；全部明细评价完，订单 3→4。
 * 权限：ADMIN 全站管理，MERCHANT 仅本店。删除和恢复仅 ADMIN。
 */
@Service
public class ReviewServiceImpl extends ServiceImpl<ReviewMapper, Review> implements ReviewService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderItemMapper orderItemMapper;
    @Autowired
    private OrderStatusLogMapper orderStatusLogMapper;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private OwnershipChecker ownershipChecker;
    @Autowired
    private ReviewMapper reviewMapper; // for custom methods

    @Override
    @Transactional
    public Review submitReview(Review review) {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new BusinessException(ResultCode.UNAUTHORIZED);

        Order order = validateReviewOrder(userId, review.getOrderId());
        OrderItem oi = validateReviewOrderItem(review, order.getId());
        checkNotDuplicateReview(review.getOrderItemId());
        validateRating(review.getRating());

        review.setId(null);
        review.setUserId(userId);
        review.setProductId(oi.getProductId());
        review.setShopId(oi.getShopId());
        this.save(review);

        tryCompleteOrder(order, userId);
        return review;
    }

    private Order validateReviewOrder(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) throw new BusinessException(ResultCode.NOT_FOUND);
        if (!userId.equals(order.getUserId())) throw new BusinessException(ResultCode.FORBIDDEN);
        if (order.getStatus() == null || order.getStatus() != 3) {
            throw new BusinessException("仅待评价状态的订单可评价");
        }
        return order;
    }

    private OrderItem validateReviewOrderItem(Review review, Long orderId) {
        OrderItem oi = orderItemMapper.selectById(review.getOrderItemId());
        if (oi == null || !oi.getOrderId().equals(orderId)) {
            throw new BusinessException("订单明细不存在");
        }
        if (oi.getRefundStatus() != null && oi.getRefundStatus() > 0) {
            throw new BusinessException("该商品正在退款或已退款，无法评价");
        }
        if (oi.getCancelStatus() != null && oi.getCancelStatus() > 0) {
            throw new BusinessException("该商品已取消，无法评价");
        }
        return oi;
    }

    private void checkNotDuplicateReview(Long orderItemId) {
        LambdaQueryWrapper<Review> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(Review::getOrderItemId, orderItemId);
        if (this.count(existWrapper) > 0) {
            throw new BusinessException("该商品已评价，不可重复提交");
        }
    }

    private void validateRating(Integer rating) {
        if (rating == null || rating < 1 || rating > 5) {
            throw new BusinessException("评分必须在 1-5 之间");
        }
    }

    private void tryCompleteOrder(Order order, Long userId) {
        List<OrderItem> allItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
        long needReviewCount = allItems.stream()
                .filter(i -> (i.getRefundStatus() == null || i.getRefundStatus() == 0)
                        && (i.getCancelStatus() == null || i.getCancelStatus() == 0))
                .count();
        LambdaQueryWrapper<Review> orderReviewWrapper = new LambdaQueryWrapper<>();
        orderReviewWrapper.eq(Review::getOrderId, order.getId());
        long reviewedCount = this.count(orderReviewWrapper);

        if (reviewedCount < needReviewCount) return;

        int from = order.getStatus();
        OrderStatus.checkTransition(from, 4);
        order.setStatus(4);
        orderMapper.updateById(order);

        OrderStatusLog log = new OrderStatusLog();
        log.setOrderId(order.getId());
        log.setFromStatus(from);
        log.setToStatus(4);
        log.setOperatorId(userId);
        log.setOperatorRole("USER");
        log.setRemark("全部商品已评价，订单完成");
        orderStatusLogMapper.insert(log);
    }

    @Override
    public PageResult<Map<String, Object>> myReviews(int current, int size) {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new BusinessException(ResultCode.UNAUTHORIZED);

        Page<Review> pageParam = new Page<>(current, size);
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Review::getUserId, userId);
        wrapper.orderByDesc(Review::getCreateTime);

        Page<Review> pageResult = this.page(pageParam, wrapper);
        List<Map<String, Object>> records = buildRecords(pageResult.getRecords());

        PageResult<Map<String, Object>> pr = new PageResult<>();
        pr.setTotal(pageResult.getTotal());
        pr.setPages(pageResult.getPages());
        pr.setCurrent(pageResult.getCurrent());
        pr.setSize(pageResult.getSize());
        pr.setRecords(records);
        return pr;
    }

    @Override
    public void reply(Long reviewId, String reply) {
        Review review = this.getById(reviewId);
        if (review == null) throw new BusinessException(ResultCode.NOT_FOUND);
        // 归属校验：商家只能回复自己店铺商品的评价
        ownershipChecker.assertOrderOwned(review.getOrderId());

        review.setReply(reply);
        this.updateById(review);
    }

    @Override
    public PageResult<Review> productReviews(Long productId, int current, int size) {
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Review::getProductId, productId)
               .orderByDesc(Review::getCreateTime);
        Page<Review> page = this.page(new Page<>(current, size), wrapper);
        return PageResult.of(page);
    }

    @Override
    public PageResult<Map<String, Object>> managePage(int current, int size, Long productId,
                                                       Integer rating, Integer hasReply,
                                                       Integer showDeleted) {
        List<Review> reviewList;
        long total;

        if (showDeleted != null && showDeleted == 1) {
            // 查已删除（绕过 MP @TableLogic 自动过滤）
            List<Long> shopIds = ownershipChecker.myShopIds();
            Page<Review> page = reviewMapper.selectManageWithDeleted(
                    new Page<>(current, size), shopIds, productId, rating, hasReply);
            reviewList = page.getRecords();
            total = reviewMapper.countManageWithDeleted(shopIds, productId, rating, hasReply);
        } else {
            // 正常查询（MP 自动过滤 deleted=0）
            LambdaQueryWrapper<Review> wrapper = buildManageWrapper(productId, rating, hasReply);
            wrapper.orderByDesc(Review::getCreateTime);
            reviewList = this.page(new Page<>(current, size), wrapper).getRecords();

            LambdaQueryWrapper<Review> countWrapper = buildManageWrapper(productId, rating, hasReply);
            total = this.count(countWrapper);
        }

        // 组装结果 — 附加用户名/商品名/订单号
        List<Map<String, Object>> records = buildRecords(reviewList);

        PageResult<Map<String, Object>> pr = new PageResult<>();
        pr.setTotal(total);
        pr.setPages((long) Math.ceil((double) total / size));
        pr.setCurrent((long) current);
        pr.setSize((long) size);
        pr.setRecords(records);
        return pr;
    }

    /** 构建管理端评价查询条件（含店铺归属、商品、评分、回复状态过滤） */
    private LambdaQueryWrapper<Review> buildManageWrapper(Long productId, Integer rating, Integer hasReply) {
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<>();
        // MERCHANT 只看自家店
        List<Long> shopIds = ownershipChecker.myShopIds();
        if (shopIds != null) {
            if (shopIds.isEmpty()) {
                wrapper.eq(Review::getId, -1L);
            } else {
                wrapper.in(Review::getShopId, shopIds);
            }
        }
        if (productId != null) {
            wrapper.eq(Review::getProductId, productId);
        }
        if (rating != null) {
            wrapper.eq(Review::getRating, rating);
        }
        if (hasReply != null) {
            applyHasReplyFilter(wrapper, hasReply);
        }
        return wrapper;
    }

    /** 应用"是否已回复"过滤条件 */
    private void applyHasReplyFilter(LambdaQueryWrapper<Review> wrapper, int hasReply) {
        if (hasReply == 1) {
            wrapper.isNotNull(Review::getReply).ne(Review::getReply, "");
        } else {
            wrapper.and(w -> w.isNull(Review::getReply).or().eq(Review::getReply, ""));
        }
    }

    /** 批量构建评价 VO（含用户名/商品名/规格/订单号） */
    private List<Map<String, Object>> buildRecords(List<Review> reviewList) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (Review r : reviewList) {
            records.add(buildSingleReviewVO(r));
        }
        return records;
    }

    /** 构建单条评价 VO Map */
    private Map<String, Object> buildSingleReviewVO(Review r) {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("id", r.getId());
        vo.put("orderId", r.getOrderId());
        vo.put("orderItemId", r.getOrderItemId());
        vo.put("userId", r.getUserId());
        vo.put("productId", r.getProductId());
        vo.put("shopId", r.getShopId());
        vo.put("rating", r.getRating());
        vo.put("content", r.getContent());
        vo.put("images", r.getImages());
        vo.put("reply", r.getReply());
        vo.put("createTime", r.getCreateTime());
        vo.put("deleted", r.getDeleted());
        enrichWithUserInfo(vo, r.getUserId());
        enrichWithProductInfo(vo, r.getProductId());
        enrichWithOrderInfo(vo, r.getOrderItemId(), r.getOrderId());
        return vo;
    }

    /** 填充用户昵称/用户名 */
    private void enrichWithUserInfo(Map<String, Object> vo, Long userId) {
        User u = userMapper.selectById(userId);
        vo.put("username", u != null ? u.getUsername() : "");
        vo.put("nickname", u != null ? u.getNickname() : "");
    }

    /** 填充商品名/商品图 */
    private void enrichWithProductInfo(Map<String, Object> vo, Long productId) {
        Product product = productMapper.selectById(productId);
        vo.put("productName", product != null ? product.getName() : "");
        vo.put("productImage", product != null ? product.getMainImage() : "");
    }

    /** 填充规格名和订单号 */
    private void enrichWithOrderInfo(Map<String, Object> vo, Long orderItemId, Long orderId) {
        if (orderItemId != null) {
            OrderItem oi = orderItemMapper.selectById(orderItemId);
            vo.put("specName", oi != null ? oi.getSpecName() : "");
        } else {
            vo.put("specName", "");
        }
        Order order = orderMapper.selectById(orderId);
        vo.put("orderNo", order != null ? order.getOrderNo() : "");
    }

    @Override
    @Transactional
    public void deleteReview(Long reviewId) {
        Review review = this.getById(reviewId);
        if (review == null) throw new BusinessException(ResultCode.NOT_FOUND);
        // 只允许 ADMIN 删除
        if (!"ADMIN".equals(UserContext.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        // 逻辑删除（BaseEntity 带 @TableLogic）
        this.removeById(reviewId);
    }

    @Override
    @Transactional
    public void restoreReview(Long reviewId) {
        // 校验角色
        if (!"ADMIN".equals(UserContext.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        int affected = reviewMapper.restoreById(reviewId);
        if (affected == 0) {
            throw new BusinessException("评价不存在或未删除");
        }
    }
}
