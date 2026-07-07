package com.petshop.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.petshop.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 用户宠物档案（推荐深化：个人信息进入推荐的核心数据） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_pet")
public class UserPet extends BaseEntity {

    private Long userId;
    /** 宠物昵称 */
    private String name;
    /** 种类 1猫咪 2狗狗 3兔子 4鸟类 9其他（对齐 tag 字典） */
    private Integer species;
    /** 品种 如英短/金毛 */
    private String breed;
    /** 1公 2母 */
    private Integer gender;
    /** 生日：<1岁幼年 1~7岁成年 >7岁老年 */
    private LocalDate birthday;
    /** 体重kg：犬≥15大型 <15小型 */
    private BigDecimal weightKg;
    /** 是否绝育 0否 1是 */
    private Integer sterilized;
}
