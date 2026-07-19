package com.petshop.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.petshop.user.entity.UserPet;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserPetMapper extends BaseMapper<UserPet> {
}
