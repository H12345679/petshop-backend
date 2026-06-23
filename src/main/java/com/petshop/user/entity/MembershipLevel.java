package com.petshop.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.petshop.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 会员等级 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("membership_level")
public class MembershipLevel extends BaseEntity {

    /** 等级序号 越大越高 */
    private Integer level;
    private String name;
    /** 折扣 1=无折扣 0.90=9折 */
    private BigDecimal discount;
    /** 升级所需积分 */
    private Integer threshold;
    private String icon;
    private String description;
}
