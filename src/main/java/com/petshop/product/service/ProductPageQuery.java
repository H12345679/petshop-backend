package com.petshop.product.service;

import com.petshop.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品分页查询参数。
 * 继承 PageQuery（自带 current/size + toPage()），只加业务过滤字段。
 * 注意：id 类字段必须用 Long（雪花 id 19 位，int 会溢出）；过滤字段用包装类型才能为 null（表示不过滤）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "商品列表分页搜索参数")
public class ProductPageQuery extends PageQuery {

    @Schema(description = "按单个门店过滤", example = "180479302948019283")
    private Long shopId;

    @Schema(description = "按多个门店过滤（逗号分隔）", example = "1,2,3")
    private String shopIds;

    @Schema(description = "按分类过滤")
    private Long categoryId;

    @Schema(description = "商品名（模糊搜索）")
    private String name;

    @Schema(description = "类型 1宠物 2周边")
    private Integer type;

    @Schema(description = "上下架 1上架 0下架")
    private Integer status;

    @Schema(description = "最低价")
    private java.math.BigDecimal minPrice;

    @Schema(description = "最高价")
    private java.math.BigDecimal maxPrice;

    @Schema(description = "排序方式: sales_desc, price_asc, price_desc, new (默认)")
    private String sort;
}
