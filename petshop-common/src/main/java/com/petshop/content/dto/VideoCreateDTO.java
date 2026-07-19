package com.petshop.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 视频元数据创建/更新请求体 DTO (E 模块 - 视频创建接口)
 */
@Data
@Schema(description = "视频创建/更新请求体")
public class VideoCreateDTO {

    @Schema(description = "视频标题", example = "调皮的小加菲猫吃罐头瞬间")
    @NotBlank(message = "视频标题不能为空")
    private String title;

    @Schema(description = "封面图片URL", example = "http://img.petshop.com/covers/cat_cover.jpg")
    private String cover;

    @Schema(description = "视频文件URL(由上传接口返回)", example = "http://img.petshop.com/videos/cute_cat.mp4")
    @NotBlank(message = "视频文件URL不能为空")
    private String url;

    @Schema(description = "视频简介", example = "这是店里新来的加菲猫，超能吃，喜欢的速来！")
    private String description;

    @Schema(description = "关联商品ID(0=不关联)", example = "180479402837490001")
    private Long productId;

    @Schema(description = "所属店铺ID", example = "180479302948019283")
    @NotNull(message = "店铺ID不能为空")
    private Long shopId;

    @Schema(description = "视频状态: 1上架 0下架, 默认1", example = "1")
    private Integer status = 1;
}
