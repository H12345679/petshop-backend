package com.petshop.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.petshop.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 评价（一个订单明细只能评一次） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("review")
public class Review extends BaseEntity {

    private Long orderId;
    private Long orderItemId;
    private Long userId;
    private Long productId;
    private Long shopId;
    /** 评分 1-5 */
    private Integer rating;
    private String content;
    /** 评价图 JSON */
    private String images;
    /** 商家回复 */
    private String reply;
}
