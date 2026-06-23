package com.petshop.content.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.petshop.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 宠物视频 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("video")
public class Video extends BaseEntity {

    private String title;
    private String cover;
    private String url;
    private String description;
    /** 关联商品id 0=无 */
    private Long productId;
    private Long shopId;
    private Integer views;
    /** 1上架 0下架 */
    private Integer status;
}
