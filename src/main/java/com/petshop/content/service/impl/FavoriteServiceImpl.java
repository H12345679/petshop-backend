package com.petshop.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.petshop.common.BusinessException;
import com.petshop.common.PageResult;
import com.petshop.content.entity.Favorite;
import com.petshop.content.entity.UserBehavior;
import com.petshop.content.mapper.FavoriteMapper;
import com.petshop.content.mapper.UserBehaviorMapper;
import com.petshop.content.service.FavoriteService;
import com.petshop.security.UserContext;
import com.petshop.product.entity.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FavoriteServiceImpl extends ServiceImpl<FavoriteMapper, Favorite> implements FavoriteService {

    @Autowired
    private UserBehaviorMapper userBehaviorMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addFavorite(Long productId) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }

        // 检查是否已收藏
        LambdaQueryWrapper<Favorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Favorite::getUserId, userId)
               .eq(Favorite::getProductId, productId);
        Long count = this.baseMapper.selectCount(wrapper);
        if (count > 0) {
            return; // 已经收藏，直接返回
        }

        // 插入收藏记录
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setProductId(productId);
        this.baseMapper.insert(favorite);

        // 记录用户行为 (behavior_type = 2 收藏)
        UserBehavior behavior = new UserBehavior();
        behavior.setUserId(userId);
        behavior.setProductId(productId);
        behavior.setBehaviorType(2);
        userBehaviorMapper.insert(behavior);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeFavorite(Long productId) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }

        LambdaQueryWrapper<Favorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Favorite::getUserId, userId)
               .eq(Favorite::getProductId, productId);
        this.baseMapper.delete(wrapper);
    }

    @Override
    public int checkFavorite(Long productId) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return 0;
        }
        LambdaQueryWrapper<Favorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Favorite::getUserId, userId)
               .eq(Favorite::getProductId, productId);
        Long count = this.baseMapper.selectCount(wrapper);
        return count > 0 ? 1 : 0;
    }

    @Override
    public PageResult<Product> getFavoriteList(long current, long size) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }

        Page<Product> page = new Page<>(current, size);
        Page<Product> resultPage = this.baseMapper.selectFavoriteProducts(page, userId);

        PageResult<Product> pageResult = new PageResult<>();
        pageResult.setTotal(resultPage.getTotal());
        pageResult.setPages(resultPage.getPages());
        pageResult.setCurrent(resultPage.getCurrent());
        pageResult.setSize(resultPage.getSize());
        pageResult.setRecords(resultPage.getRecords());
        
        return pageResult;
    }
}
