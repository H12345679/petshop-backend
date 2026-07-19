package com.petshop.recommend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 推荐结果（派生表，离线算好在线查，无 deleted） */
@Data
@TableName("recommend_result")
public class RecommendResult implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long productId;
    /** 推荐得分 */
    private BigDecimal score;
    /** 算法来源 UCF=基于用户 */
    private String source;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
