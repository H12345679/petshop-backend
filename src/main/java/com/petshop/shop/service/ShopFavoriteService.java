package com.petshop.shop.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.petshop.common.utils.PageQuery;
import com.petshop.common.utils.PageResult;
import com.petshop.shop.entity.Shop;
import com.petshop.shop.entity.ShopFavorite;

public interface ShopFavoriteService extends IService<ShopFavorite> {
    
    void addFavorite(Long shopId);
    
    void removeFavorite(Long shopId);
    
    boolean checkFavorite(Long shopId);
    
    PageResult<Shop> pageMyFavorites(PageQuery query);
}
