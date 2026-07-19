package com.petshop.content.dto;

import com.petshop.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 视频分页查询参数 DTO (E 模块 - 视频列表接口)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "视频分页查询参数")
public class VideoPageQuery extends PageQuery {

    @Schema(description = "标题模糊检索(可选)")
    private String title;

    @Schema(description = "关联商品ID过滤(可选, 0=全部)")
    private Long productId;

    @Schema(description = "关联店铺ID过滤(可选)")
    private Long shopId;

    @Schema(description = "视频状态: 1上架 0下架(可选, 不传则查全部)")
    private Integer status;

    @Schema(description = "商品分类ID(用于根据视频关联的商品来进行分类)")
    private Long productCategoryId;
}
