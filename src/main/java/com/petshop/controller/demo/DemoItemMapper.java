package com.petshop.controller.demo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 演示 Mapper（继承 BaseMapper 即获得全部单表 CRUD）。
 */
@Mapper
public interface DemoItemMapper extends BaseMapper<DemoItem> {
}
