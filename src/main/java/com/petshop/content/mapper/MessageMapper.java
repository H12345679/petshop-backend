package com.petshop.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.petshop.content.entity.Message;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {
}
