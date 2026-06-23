package com.petshop.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.petshop.shop.entity.Product;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}
