package com.petshop.recommend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.petshop.recommend.entity.UserSimilarity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserSimilarityMapper extends BaseMapper<UserSimilarity> {
}
