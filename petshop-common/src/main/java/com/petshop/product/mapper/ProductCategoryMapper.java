package com.petshop.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.petshop.product.entity.ProductCategory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductCategoryMapper extends BaseMapper<ProductCategory> {
}
