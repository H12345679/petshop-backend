package com.petshop.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.petshop.common.PageResult;
import com.petshop.demo.entity.DemoItem;
import com.petshop.demo.service.DemoItemPageQuery;
import com.petshop.demo.mapper.DemoItemMapper;
import com.petshop.demo.service.DemoItemService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 演示 Service 实现。
 * <p>
 * 规范：继承 ServiceImpl&lt;Mapper, Entity&gt;，自动获得 save / updateById / removeById / getById / list 等方法。
 * 自定义业务方法在此实现。
 */
@Service
public class DemoItemServiceImpl extends ServiceImpl<DemoItemMapper, DemoItem> implements DemoItemService {

    @Override
    public PageResult<DemoItem> page(DemoItemPageQuery query) {
        // 1. 构建查询条件
        LambdaQueryWrapper<DemoItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(query.getName()), DemoItem::getName, query.getName());
        wrapper.orderByDesc(DemoItem::getCreateTime);

        // 2. 执行分页查询（query.toPage() 自动处理页码和每页条数上限）
        Page<DemoItem> page = this.page(query.toPage(), wrapper);

        // 3. 转成统一分页结果
        return PageResult.of(page);
    }
}
