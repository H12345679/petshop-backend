package com.petshop.recommend.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.petshop.common.BaseEntityLite;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品收藏。继承 BaseEntityLite（无 deleted），走物理删除，配合唯一键支持收藏/取消收藏切换。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("favorite")
public class Favorite extends BaseEntityLite {

    private Long userId;
    private Long productId;
}
