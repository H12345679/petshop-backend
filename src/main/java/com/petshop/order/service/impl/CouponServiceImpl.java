package com.petshop.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.petshop.common.BusinessException;
import com.petshop.common.ResultCode;
import com.petshop.order.entity.Coupon;
import com.petshop.order.entity.UserCoupon;
import com.petshop.order.mapper.CouponMapper;
import com.petshop.order.mapper.UserCouponMapper;
import com.petshop.order.service.CouponService;
import com.petshop.security.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 优惠券 Service 实现。
 */
@Service
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements CouponService {

    @Autowired
    private UserCouponMapper userCouponMapper;

    // ==================== 前台 ====================

    @Override
    public List<Coupon> listAvailable() {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<Coupon> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Coupon::getStatus, 1)
               .le(Coupon::getStartTime, now)
               .ge(Coupon::getEndTime, now)
               .gt(Coupon::getRemain, 0)
               .orderByDesc(Coupon::getCreateTime);
        return this.list(wrapper);
    }

    @Override
    @Transactional
    public void receive(Long couponId) {
        Long userId = requireUserId();

        // 1) 校验优惠券存在且有效
        Coupon coupon = this.getById(couponId);
        if (coupon == null || coupon.getStatus() == null || coupon.getStatus() != 1) {
            throw new BusinessException("优惠券不存在或已下架");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getStartTime()) || now.isAfter(coupon.getEndTime())) {
            throw new BusinessException("不在优惠券有效期内");
        }
        if (coupon.getRemain() == null || coupon.getRemain() <= 0) {
            throw new BusinessException("优惠券已被领完");
        }

        // 2) 检查用户是否已领过（防重复）
        LambdaQueryWrapper<UserCoupon> ucWrapper = new LambdaQueryWrapper<>();
        ucWrapper.eq(UserCoupon::getUserId, userId)
                 .eq(UserCoupon::getCouponId, couponId);
        if (userCouponMapper.selectCount(ucWrapper) > 0) {
            throw new BusinessException("您已领取过该优惠券");
        }

        // 3) CAS 扣减库存（乐观锁，防超发）
        boolean decr = this.update(new LambdaUpdateWrapper<Coupon>()
                .eq(Coupon::getId, couponId)
                .gt(Coupon::getRemain, 0)
                .setSql("remain = remain - 1"));
        if (!decr) {
            throw new BusinessException("优惠券已被领完");
        }

        // 4) 给用户发券
        UserCoupon uc = new UserCoupon();
        uc.setUserId(userId);
        uc.setCouponId(couponId);
        uc.setStatus(0); // 0=未使用
        userCouponMapper.insert(uc);
    }

    @Override
    public List<UserCoupon> listMyCoupons(Integer status) {
        Long userId = requireUserId();
        LambdaQueryWrapper<UserCoupon> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCoupon::getUserId, userId);
        if (status != null) {
            wrapper.eq(UserCoupon::getStatus, status);
        }
        wrapper.orderByDesc(UserCoupon::getCreateTime);
        return userCouponMapper.selectList(wrapper);
    }

    // ==================== 后台 ====================

    @Override
    public void createCoupon(Coupon coupon) {
        coupon.setId(null);
        // remain 初始等于 total
        if (coupon.getRemain() == null) {
            coupon.setRemain(coupon.getTotal());
        }
        if (coupon.getStatus() == null) {
            coupon.setStatus(1);
        }
        this.save(coupon);
    }

    @Override
    public void updateCoupon(Long id, Coupon coupon) {
        Coupon exist = this.getById(id);
        if (exist == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        coupon.setId(id);
        this.updateById(coupon);
    }

    @Override
    public Page<Coupon> managePage(int current, int size, String name, Integer type, Integer status) {
        LambdaQueryWrapper<Coupon> wrapper = new LambdaQueryWrapper<>();
        if (name != null && !name.isEmpty()) {
            wrapper.like(Coupon::getName, name);
        }
        if (type != null) {
            wrapper.eq(Coupon::getType, type);
        }
        if (status != null) {
            wrapper.eq(Coupon::getStatus, status);
        }
        wrapper.orderByDesc(Coupon::getCreateTime);
        return this.page(new Page<>(current, size), wrapper);
    }

    @Override
    @Transactional
    public void deleteCoupon(Long id) {
        Coupon exist = this.getById(id);
        if (exist == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        // 删除关联的用户优惠券记录
        LambdaQueryWrapper<UserCoupon> ucWrapper = new LambdaQueryWrapper<>();
        ucWrapper.eq(UserCoupon::getCouponId, id);
        userCouponMapper.delete(ucWrapper);
        // 删除优惠券本身
        this.removeById(id);
    }

    // ========== 内部 ==========

    private Long requireUserId() {
        Long uid = UserContext.getUserId();
        if (uid == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return uid;
    }
}
