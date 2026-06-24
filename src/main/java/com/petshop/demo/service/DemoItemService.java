package com.petshop.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.petshop.common.PageResult;
import com.petshop.demo.entity.DemoItem;

/**
 * 演示 Service 接口。
 * <p>
 * 规范：各模块 Service 接口继承 IService&lt;Entity&gt;，获得全部单表 CRUD；
 * 业务方法按需在接口中声明。
 */
public interface DemoItemService extends IService<DemoItem> {

    /**
     * 分页查询（可带名称模糊搜索）。
     */
    PageResult<DemoItem> page(DemoItemPageQuery query);
}
