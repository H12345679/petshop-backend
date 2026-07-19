package com.petshop.content.vo;

import com.petshop.content.entity.Video;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 视频详情 VO（视频播放页返回，含关联商品基本信息，支持"可跳商品"功能）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "视频详情（含关联商品）")
public class VideoDetailVO extends Video {

    // ---- 关联商品信息（productId == 0 时以下字段均为 null）----

    @Schema(description = "关联商品名称")
    private String productName;

    @Schema(description = "关联商品主图URL")
    private String productMainImage;

    @Schema(description = "关联商品售价")
    private BigDecimal productPrice;

    @Schema(description = "关联商品上架状态：1上架 0下架（为0时前端可提示商品已下架）")
    private Integer productStatus;
}
