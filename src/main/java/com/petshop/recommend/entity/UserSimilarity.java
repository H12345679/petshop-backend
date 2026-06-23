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

/** 用户相似度（派生表，定时任务重算，无 deleted） */
@Data
@TableName("user_similarity")
public class UserSimilarity implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 目标用户 */
    private Long userId;
    /** 相似用户 */
    private Long simUserId;
    /** 相似度 0~1 */
    private BigDecimal similarity;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
