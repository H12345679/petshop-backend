package com.petshop.user.service;

import com.petshop.user.entity.MembershipLevel;
import com.petshop.user.model.vo.UpgradeVO;
import java.math.BigDecimal;

import java.util.List;

public interface MembershipLevelService {

    /** 查询所有会员等级 */
    List<MembershipLevel> listAll();

    /** 会员等级升级：根据积分自动匹配最高可达到的等级 */
    UpgradeVO upgrade(Long userId);

    /** 获取当前登录用户的折扣率 (无折算1.0) */
    BigDecimal getCurrentUserDiscount();

    /** 获取当前登录用户的会员等级名称 (未登录或非会员返回null) */
    String getCurrentUserLevelName();

    /** 按用户 ID 查折扣率（供内部接口/Feign 调用；userId 为 null 视为未登录，返回 1.0） */
    BigDecimal discountForUser(Long userId);

    /** 按用户 ID 查会员等级名称（供内部接口/Feign 调用；无等级返回 null） */
    String levelNameForUser(Long userId);
}
