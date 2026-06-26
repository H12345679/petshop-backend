package com.petshop.product.service;

import com.petshop.common.PageQuery;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品分页查询参数。
 * 继承 PageQuery（自带 current/size + toPage()），只加业务过滤字段。
 * 注意：id 类字段必须用 Long（雪花 id 19 位，int 会溢出）；过滤字段用包装类型才能为 null（表示不过滤）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("商品列表分页搜索参数")
public class ProductPageQuery extends PageQuery {

    @ApiModelProperty(value = "按门店过滤", example = "180479302948019283")
    private Long shopId;

    @ApiModelProperty(value = "按分类过滤")
    private Long categoryId;

    @ApiModelProperty(value = "商品名（模糊搜索）")
    private String name;

    @ApiModelProperty(value = "类型 1宠物 2周边")
    private Integer type;

    @ApiModelProperty(value = "上下架 1上架 0下架")
    private Integer status;

    @ApiModelProperty(value = "最低价")
    private java.math.BigDecimal minPrice;

    @ApiModelProperty(value = "最高价")
    private java.math.BigDecimal maxPrice;

    @ApiModelProperty(value = "排序方式: sales_desc, price_asc, price_desc, new (默认)")
    private String sort;
}
