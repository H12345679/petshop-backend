package com.petshop.shop.service;

import com.petshop.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商店分页查询参数。
 * <p>
 * 继承 PageQuery（自带 current/size + toPage()），只加本业务的过滤字段。
 * Controller 里直接作为方法入参，前端用 ?current=1&size=10&name=xx&status=1 传进来。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "商店分页查询参数")
public class ShopPageQuery extends PageQuery {

    @Schema(description = "商店名称（模糊搜索）", example = "极客宠物")
    private String name;

    @Schema(description = "营业状态 1营业 0停业", example = "1")
    private Integer status;

    @Schema(description = "店主ID", example = "1")
    private Long ownerId;
}
