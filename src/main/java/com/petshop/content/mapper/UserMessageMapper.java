package com.petshop.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.petshop.content.entity.UserMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMessageMapper extends BaseMapper<UserMessage> {
}
