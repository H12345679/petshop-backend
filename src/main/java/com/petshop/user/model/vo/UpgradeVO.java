package com.petshop.user.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 会员升级结果 */
@Data
@AllArgsConstructor
public class UpgradeVO {

    /** 旧等级名称 */
    private String oldLevel;
    /** 新等级名称 */
    private String newLevel;
    /** 当前积分 */
    private Integer currentPoints;
}
