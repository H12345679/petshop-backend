package com.petshop.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.petshop.content.entity.UserBehavior;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户行为记录 Mapper 接口
 */
@Mapper
public interface UserBehaviorMapper extends BaseMapper<UserBehavior> {
}
