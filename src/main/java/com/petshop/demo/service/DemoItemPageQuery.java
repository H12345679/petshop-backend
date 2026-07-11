package com.petshop.demo.service;

import com.petshop.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * DemoItem 分页查询参数。
 * <p>
 * 演示如何继承 PageQuery 添加业务查询条件。
 * 各模块照此模式创建自己的 XxxPageQuery。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "DemoItem 分页查询参数")
public class DemoItemPageQuery extends PageQuery {

    @Schema(description = "名称（模糊搜索）", example = "测试")
    private String name;
}
