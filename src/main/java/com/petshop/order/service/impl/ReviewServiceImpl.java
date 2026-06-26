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

    @Override
    @Transactional
    public Review submitReview(Review review) {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new BusinessException(ResultCode.UNAUTHORIZED);

        // 1) 校验订单
        Order order = orderMapper.selectById(review.getOrderId());
        if (order == null) throw new BusinessException(ResultCode.NOT_FOUND);
        if (!userId.equals(order.getUserId())) throw new BusinessException(ResultCode.FORBIDDEN);
        if (order.getStatus() == null || order.getStatus() != 3) {
            throw new BusinessException("仅待评价状态的订单可评价");
        }

        // 2) 校验订单明细
        OrderItem oi = orderItemMapper.selectById(review.getOrderItemId());
        if (oi == null || !oi.getOrderId().equals(order.getId())) {
            throw new BusinessException("订单明细不存在");
        }

        // 3) 不允许重复评价（uk_item 约束会兜底，这里提前校验给友好提示）
        LambdaQueryWrapper<Review> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(Review::getOrderItemId, review.getOrderItemId());
        if (this.count(existWrapper) > 0) {
            throw new BusinessException("该商品已评价，不可重复提交");
        }

        // 4) 评分校验
        if (review.getRating() == null || review.getRating() < 1 || review.getRating() > 5) {
            throw new BusinessException("评分必须在 1-5 之间");
        }

        // 5) 填充字段
        review.setId(null);
        review.setUserId(userId);
        review.setProductId(oi.getProductId());
        review.setShopId(oi.getShopId());
        this.save(review);

        // 6) 检查该订单下所有明细是否都已评价，若全评则订单 3→4
        List<OrderItem> allItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
        // 查询该订单下已有的评价数
        LambdaQueryWrapper<Review> orderReviewWrapper = new LambdaQueryWrapper<>();
        orderReviewWrapper.eq(Review::getOrderId, order.getId());
        long reviewedCount = this.count(orderReviewWrapper);

        if (reviewedCount >= allItems.size()) {
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

        return review;
    }

    @Override
    public void reply(Long reviewId, String reply) {
        Review review = this.getById(reviewId);
        if (review == null) throw new BusinessException(ResultCode.NOT_FOUND);
        // 归属校验：商家只能回复自己店铺商品的评价
        ownershipChecker.assertOrderOwned(review.getOrderId());
        // 实际上应该校验 review.getShopId；这里通过 order→shop 间接校验

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
                                                       Integer rating, Integer hasReply) {
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<>();

        // MERCHANT 只看自家店
        List<Long> shopIds = ownershipChecker.myShopIds();
        if (shopIds != null) {
            wrapper.in(Review::getShopId, shopIds);
        }
        if (productId != null) {
            wrapper.eq(Review::getProductId, productId);
        }
        if (rating != null) {
            wrapper.eq(Review::getRating, rating);
        }
        if (hasReply != null) {
            if (hasReply == 1) {
                wrapper.isNotNull(Review::getReply).ne(Review::getReply, "");
            } else {
                wrapper.and(w -> w.isNull(Review::getReply).or().eq(Review::getReply, ""));
            }
        }
        wrapper.orderByDesc(Review::getCreateTime);
        Page<Review> page = this.page(new Page<>(current, size), wrapper);

        // 组装结果，附加用户名
        List<Map<String, Object>> records = new ArrayList<>();
        for (Review r : page.getRecords()) {
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
            // 查用户昵称
            User u = userMapper.selectById(r.getUserId());
            vo.put("username", u != null ? u.getUsername() : "");
            vo.put("nickname", u != null ? u.getNickname() : "");

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
}
