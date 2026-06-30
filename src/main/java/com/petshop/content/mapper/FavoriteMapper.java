package com.petshop.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.petshop.content.entity.Favorite;
import com.petshop.product.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;


/**
 * 收藏记录 Mapper 接口
 */
@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {

    /**
     * 连表分页查询当前用户收藏的商品列表
     * @param page 分页参数
     * @param userId 用户ID
     * @return 收藏的商品分页数据
     */
    Page<Product> selectFavoriteProducts(Page<Product> page, @Param("userId") Long userId);
}
