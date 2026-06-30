package com.petshop.user.service;

import com.petshop.user.entity.MembershipLevel;
import com.petshop.user.model.vo.UpgradeVO;

import java.util.List;

public interface MembershipLevelService {

    /** 查询所有会员等级 */
    List<MembershipLevel> listAll();

    /** 会员等级升级：根据积分自动匹配最高可达到的等级 */
    UpgradeVO upgrade(Long userId);
}
