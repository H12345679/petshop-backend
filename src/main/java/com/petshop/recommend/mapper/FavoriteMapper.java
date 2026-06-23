package com.petshop.recommend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.petshop.recommend.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {
}
