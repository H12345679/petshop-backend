package com.petshop.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.petshop.demo.entity.DemoItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 演示 Mapper（继承 BaseMapper 即获得全部单表 CRUD）。
 * <p>
 * 放在 com.petshop.controller.demo.mapper 包下，才能被启动类的
 * {@code @MapperScan("com.petshop.**.mapper")} 扫描到。
 */
@Mapper
public interface DemoItemMapper extends BaseMapper<DemoItem> {
}
