package com.petshop.recommend.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.petshop.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 用户行为记录（喂推荐/统计） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_behavior")
public class UserBehavior extends BaseEntity {

    private Long userId;
    private Long productId;
    /** 1浏览 2收藏 3加购 4购买 */
    private Integer behaviorType;
}
