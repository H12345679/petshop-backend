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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        LambdaQueryWrapper<Coupon> queryWrapper = new LambdaQueryWrapper<>();
        // 查询条件：1) 状态为上架(1) 2) 当前时间在活动的开始和结束时间之间 3) 剩余库存大于 0
        queryWrapper.eq(Coupon::getStatus, 1)
               .le(Coupon::getStartTime, now)
               .ge(Coupon::getEndTime, now)
               .gt(Coupon::getRemain, 0)
               .orderByDesc(Coupon::getCreateTime);
        return this.list(queryWrapper);
    }

    @Override
    @Transactional
    public void receive(Long couponId) {
        Long userId = requireUserId();

        // 1) 校验目标优惠券是否存在，以及是否处于有效的上架状态
        Coupon coupon = this.getById(couponId);
        if (coupon == null || coupon.getStatus() == null || coupon.getStatus() != 1) {
            throw new BusinessException("优惠券不存在或已下架");
        }
        
        // 校验领取时间是否在优惠券规定的有效期内
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getStartTime()) || now.isAfter(coupon.getEndTime())) {
            throw new BusinessException("当前不在优惠券的领取有效期内");
        }
        
        // 校验是否还有剩余库存
        if (coupon.getRemain() == null || coupon.getRemain() <= 0) {
            throw new BusinessException("来晚了，优惠券已被领完");
        }

        // 2) 检查当前用户是否已经领取过该优惠券（防止同一个人重复领取）
        LambdaQueryWrapper<UserCoupon> duplicateCheckWrapper = new LambdaQueryWrapper<>();
        duplicateCheckWrapper.eq(UserCoupon::getUserId, userId)
                             .eq(UserCoupon::getCouponId, couponId);
        if (userCouponMapper.selectCount(duplicateCheckWrapper) > 0) {
            throw new BusinessException("您已经领取过该优惠券了");
        }

        // 3) 使用 CAS（比较并交换）乐观锁机制安全扣减库存。
        // 通过 SQL 底层的原子性（remain = remain - 1 且限定 remain > 0），
        // 完美防止高并发抢券时出现“超发”的严重 Bug。
        boolean isStockDecremented = this.update(new LambdaUpdateWrapper<Coupon>()
                .eq(Coupon::getId, couponId)
                .gt(Coupon::getRemain, 0)
                .setSql("remain = remain - 1"));
                
        if (!isStockDecremented) {
            throw new BusinessException("来晚了，优惠券已被领完");
        }

        // 4) 库存扣减成功后，正式为该用户生成一张对应的优惠券资产
        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(userId);
        userCoupon.setCouponId(couponId);
        userCoupon.setStatus(0); // 初始状态标记为 0 (未使用)
        userCouponMapper.insert(userCoupon);
    }

    @Override
    @Transactional
    public List<Map<String, Object>> listMyCoupons(Integer status) {
        Long userId = requireUserId();

        // 惰性过期归类机制：
        // 并不是依靠定时任务去扫表，而是在用户主动查询优惠券列表时，
        // 顺手把该用户名下“仍处于未使用状态(0)”但“实际时间已过截止日期”的优惠券，
        // 批量将状态更新为已过期(2)。这样既节省了服务器后台资源，又能保证查询结果绝对准确。
        userCouponMapper.update(null, new LambdaUpdateWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getStatus, 0)
                .inSql(UserCoupon::getCouponId, "SELECT id FROM coupon WHERE end_time < NOW()")
                .set(UserCoupon::getStatus, 2));

        // 根据传入的 status 条件查询用户拥有的优惠券关联记录
        LambdaQueryWrapper<UserCoupon> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserCoupon::getUserId, userId);
        if (status != null) {
            queryWrapper.eq(UserCoupon::getStatus, status);
        }
        queryWrapper.orderByDesc(UserCoupon::getCreateTime);
        List<UserCoupon> userCoupons = userCouponMapper.selectList(queryWrapper);

        // 遍历用户的领券记录，去 coupon 主表关联出详细的优惠规则（名称、类型、金额等），
        // 包装成 Map 列表返回给前端展示，避免前端因为拿不到明细数据而显示异常（如 NaN 折）。
        List<Map<String, Object>> couponDetailsList = new ArrayList<>();
        for (UserCoupon userCoupon : userCoupons) {
            Map<String, Object> couponDetail = new LinkedHashMap<>();
            couponDetail.put("id", userCoupon.getId());
            couponDetail.put("couponId", userCoupon.getCouponId());
            couponDetail.put("status", userCoupon.getStatus());
            couponDetail.put("usedTime", userCoupon.getUsedTime());
            couponDetail.put("orderId", userCoupon.getOrderId());
            couponDetail.put("createTime", userCoupon.getCreateTime());

            // 查出原始的优惠券定义
            Coupon coupon = this.getById(userCoupon.getCouponId());
            if (coupon != null) {
                couponDetail.put("name", coupon.getName());
                couponDetail.put("type", coupon.getType());
                couponDetail.put("amount", coupon.getAmount());
                couponDetail.put("threshold", coupon.getThreshold());
                couponDetail.put("total", coupon.getTotal());
                couponDetail.put("remain", coupon.getRemain());
                couponDetail.put("startTime", coupon.getStartTime());
                couponDetail.put("endTime", coupon.getEndTime());
            }
            couponDetailsList.add(couponDetail);
        }
        return couponDetailsList;
    }

    // ==================== 后台 ====================

    @Override
    public void createCoupon(Coupon coupon) {
        // 限制优惠券最大发行量，防止数字过大导致后续计算或库存扣减溢出
        if (coupon.getTotal() != null && coupon.getTotal() > 100000) {
            throw new BusinessException("发行总量最大不能超过 100,000 张");
        }
        // 如果是折扣券(type=2)，折扣率不能超过 0.99 (即 9.9 折)
        if (coupon.getType() != null && coupon.getType() == 2) {
            if (coupon.getAmount() == null || coupon.getAmount().compareTo(new java.math.BigDecimal("0.99")) > 0) {
                throw new BusinessException("折扣率不能大于 0.99");
            }
        }
        coupon.setId(null);
        // 新建优惠券时，初始的剩余库存(remain)默认等于发行总量(total)
        if (coupon.getRemain() == null) {
            coupon.setRemain(coupon.getTotal());
        }
        // 默认状态为 1 (上架)
        if (coupon.getStatus() == null) {
            coupon.setStatus(1);
        }
        this.save(coupon);
    }

    @Override
    public void updateCoupon(Long id, Coupon coupon) {
        if (coupon.getTotal() != null && coupon.getTotal() > 100000) {
            throw new BusinessException("发行总量最大不能超过 100,000 张");
        }
        if (coupon.getType() != null && coupon.getType() == 2) {
            if (coupon.getAmount() == null || coupon.getAmount().compareTo(new java.math.BigDecimal("0.99")) > 0) {
                throw new BusinessException("折扣率不能大于 0.99");
            }
        }
        
        Coupon existingCoupon = this.getById(id);
        if (existingCoupon == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        coupon.setId(id);
        this.updateById(coupon);
    }

    @Override
    public Page<Coupon> managePage(int current, int size, String name, Integer type, Integer status) {
        LambdaQueryWrapper<Coupon> queryWrapper = new LambdaQueryWrapper<>();
        if (name != null && !name.isEmpty()) {
            queryWrapper.like(Coupon::getName, name);
        }
        if (type != null) {
            queryWrapper.eq(Coupon::getType, type);
        }
        if (status != null) {
            queryWrapper.eq(Coupon::getStatus, status);
        }
        queryWrapper.orderByDesc(Coupon::getCreateTime);
        return this.page(new Page<>(current, size), queryWrapper);
    }

    @Override
    @Transactional
    public void deleteCoupon(Long id) {
        Coupon existingCoupon = this.getById(id);
        if (existingCoupon == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        // 级联删除：先彻底清空用户已经领取的该类优惠券记录
        LambdaQueryWrapper<UserCoupon> userCouponQueryWrapper = new LambdaQueryWrapper<>();
        userCouponQueryWrapper.eq(UserCoupon::getCouponId, id);
        userCouponMapper.delete(userCouponQueryWrapper);
        
        // 最后删除主表的优惠券定义
        this.removeById(id);
    }

    // ========== 内部 ==========

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }
}
