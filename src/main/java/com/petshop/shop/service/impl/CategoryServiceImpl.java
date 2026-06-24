package com.petshop.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.petshop.shop.entity.ProductCategory;
import com.petshop.shop.mapper.ProductCategoryMapper;
import com.petshop.shop.service.CategoryService;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商品分类 Service 实现。
 * <p>
 * 数据库里分类是「平铺」存的（每行带一个 parent_id），前端要的是「树」。
 * tree() 就负责把平表组装成树。
 */
@Service
public class CategoryServiceImpl extends ServiceImpl<ProductCategoryMapper, ProductCategory>
        implements CategoryService {

    @Override
    public List<ProductCategory> tree() {
        // 1) 查出全部分类（平铺），按 sort 升序，保证前端展示顺序
        List<ProductCategory> all = this.list(
                new LambdaQueryWrapper<ProductCategory>().orderByAsc(ProductCategory::getSort));

        // 2) 按 parentId 分组：key = 父分类 id，value = 挂在它名下的所有子分类
        //    例：{ 0 -> [宠物, 宠物食品...], 1 -> [猫咪, 狗狗] }
        Map<Long, List<ProductCategory>> childrenMap = all.stream()
                .collect(Collectors.groupingBy(ProductCategory::getParentId));

        // 3) 给每个分类挂上自己的 children（用对象引用——一处赋值，整棵树就连起来了）
        for (ProductCategory c : all) {
            c.setChildren(childrenMap.getOrDefault(c.getId(), new ArrayList<>()));
        }

        // 4) 只返回顶级分类（parentId=0）。因为第 3 步是按引用挂的，
        //    顶级节点的 children 里已经装着子节点、子节点里又装着孙节点……整棵树都在了
        return childrenMap.getOrDefault(0L, new ArrayList<>());
    }
}
