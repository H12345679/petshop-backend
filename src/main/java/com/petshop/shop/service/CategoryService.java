package com.petshop.shop.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.petshop.shop.entity.ProductCategory;

import java.util.List;

/**
 * 商品分类 Service 接口。
 * <p>
 * 单表 CRUD 由 IService 白送；这里只声明「把平表组装成树」这一个业务方法。
 */
public interface CategoryService extends IService<ProductCategory> {

    /** 查询分类树：返回顶级分类，每个分类的 children 已挂好。 */
    List<ProductCategory> tree();
}
