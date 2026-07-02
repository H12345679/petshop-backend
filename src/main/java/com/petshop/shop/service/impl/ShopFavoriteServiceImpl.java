package com.petshop.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.petshop.common.exception.BusinessException;
import com.petshop.common.utils.PageQuery;
import com.petshop.common.utils.PageResult;
import com.petshop.common.utils.ResultCode;
import com.petshop.common.utils.UserContext;
import com.petshop.shop.entity.Shop;
import com.petshop.shop.entity.ShopFavorite;
import com.petshop.shop.mapper.ShopFavoriteMapper;
import com.petshop.shop.mapper.ShopMapper;
import com.petshop.shop.service.ShopFavoriteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ShopFavoriteServiceImpl extends ServiceImpl<ShopFavoriteMapper, ShopFavorite> implements ShopFavoriteService {

    @Autowired
    private ShopMapper shopMapper;

    @Override
    public void addFavorite(Long shopId) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "店铺不存在");
        }
        
        LambdaQueryWrapper<ShopFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ShopFavorite::getUserId, userId).eq(ShopFavorite::getShopId, shopId);
        ShopFavorite exist = this.getOne(wrapper);
        if (exist == null) {
            ShopFavorite fav = new ShopFavorite();
            fav.setUserId(userId);
            fav.setShopId(shopId);
            this.save(fav);
        }
    }

    @Override
    public void removeFavorite(Long shopId) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        LambdaQueryWrapper<ShopFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ShopFavorite::getUserId, userId).eq(ShopFavorite::getShopId, shopId);
        this.remove(wrapper);
    }

    @Override
    public boolean checkFavorite(Long shopId) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return false;
        }
        LambdaQueryWrapper<ShopFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ShopFavorite::getUserId, userId).eq(ShopFavorite::getShopId, shopId);
        return this.count(wrapper) > 0;
    }

    @Override
    public PageResult<Shop> pageMyFavorites(PageQuery query) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        
        LambdaQueryWrapper<ShopFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ShopFavorite::getUserId, userId).orderByDesc(ShopFavorite::getCreateTime);
        
        Page<ShopFavorite> page = this.page(query.toPage(), wrapper);
        List<ShopFavorite> records = page.getRecords();
        
        List<Shop> shops = new ArrayList<>();
        if (records != null && !records.isEmpty()) {
            List<Long> shopIds = records.stream().map(ShopFavorite::getShopId).collect(Collectors.toList());
            // 为了保持收藏时间排序，先查出来再按照 ID 重排
            List<Shop> dbShops = shopMapper.selectBatchIds(shopIds);
            java.util.Map<Long, Shop> shopMap = dbShops.stream().collect(Collectors.toMap(Shop::getId, s -> s));
            
            for (Long sid : shopIds) {
                Shop s = shopMap.get(sid);
                if (s != null) {
                    shops.add(s);
                }
            }
        }
        
        PageResult<Shop> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setSize(page.getSize());
        result.setCurrent(page.getCurrent());
        result.setRecords(shops);
        return result;
    }
}
