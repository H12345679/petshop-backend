package com.petshop.content.dto;

import com.petshop.common.PageQuery;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 视频分页查询参数 DTO (E 模块 - 视频列表接口)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("视频分页查询参数")
public class VideoPageQuery extends PageQuery {

    @ApiModelProperty("标题模糊检索(可选)")
    private String title;

    @ApiModelProperty("关联商品ID过滤(可选, 0=全部)")
    private Long productId;

    @ApiModelProperty("关联店铺ID过滤(可选)")
    private Long shopId;

    @ApiModelProperty("视频状态: 1上架 0下架(可选, 不传则查全部)")
    private Integer status;

    @ApiModelProperty("商品分类ID(用于根据视频关联的商品来进行分类)")
    private Long productCategoryId;
}
