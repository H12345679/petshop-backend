package com.petshop.content.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.petshop.common.PageResult;
import com.petshop.content.entity.Favorite;
import com.petshop.product.entity.Product;

/**
 * 收藏记录 服务类
 */
public interface FavoriteService extends IService<Favorite> {

    /**
     * 添加收藏（同时记录用户行为）
     * @param productId 商品ID
     */
    void addFavorite(Long productId);

    /**
     * 取消收藏（物理删除）
     * @param productId 商品ID
     */
    void removeFavorite(Long productId);

    /**
     * 判断是否已收藏
     * @param productId 商品ID
     * @return 1已收藏 0未收藏
     */
    int checkFavorite(Long productId);

    /**
     * 获取当前用户的收藏商品分页列表
     * @param current 页码
     * @param size 每页条数
     * @return 分页结果
     */
    PageResult<Product> getFavoriteList(long current, long size);
}
