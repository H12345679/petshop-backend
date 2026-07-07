package com.petshop.user.service;

import com.petshop.user.entity.UserPet;

import java.util.List;

/**
 * 用户宠物档案服务。
 * <p>
 * 档案除 CRUD 外，核心用途是把宠物属性映射成商品标签集合（{@link #petTagNames(Long)}），
 * 无缝接入现有 tag/product_tag 推荐链路——即"个人信息进入推荐"的最短路径。
 */
public interface UserPetService {

    /** 当前用户的宠物列表 */
    List<UserPet> myPets();

    /** 新增宠物（归属当前用户） */
    void addPet(UserPet pet);

    /** 修改宠物（校验归属） */
    void updatePet(Long id, UserPet pet);

    /** 删除宠物（校验归属） */
    void deletePet(Long id);

    /**
     * 宠物档案 → 标签名集合。
     * 规则：species→猫咪/狗狗/兔子/鸟类；生日→幼年(&lt;1岁)/成年(1~7)/老年(&gt;7)；
     * 犬类体重→大型犬(≥15kg)/小型犬(&lt;15kg)。
     * @return 无档案时返回空列表
     */
    List<String> petTagNames(Long userId);

    /** 用户养的物种集合（1猫 2狗 3兔 4鸟），用于人群召回与冲突过滤 */
    List<Integer> petSpecies(Long userId);
}
