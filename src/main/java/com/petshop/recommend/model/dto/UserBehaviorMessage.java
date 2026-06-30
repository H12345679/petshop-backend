package com.petshop.recommend.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserBehaviorMessage implements Serializable {
    private Long userId;
    private Long productId;
    private Integer behaviorType; // 1:浏览 2:收藏 3:加购 4:购买
}
