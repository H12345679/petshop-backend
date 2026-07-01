package com.petshop.user.service.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.petshop.common.BusinessException;
import com.petshop.user.entity.MembershipLevel;
import com.petshop.user.entity.User;
import com.petshop.user.mapper.MembershipLevelMapper;
import com.petshop.user.mapper.UserMapper;
import com.petshop.user.model.vo.UpgradeVO;
import com.petshop.user.service.MembershipLevelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MembershipLevelServiceImpl implements MembershipLevelService {

    @Autowired
    private MembershipLevelMapper membershipLevelMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    public List<MembershipLevel> listAll() {
        QueryWrapper<MembershipLevel> qw = new QueryWrapper<>();
        qw.orderByAsc("level");
        return membershipLevelMapper.selectList(qw);
    }

    @Override
    @Transactional
    public UpgradeVO upgrade(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        int currentPoints = user.getPoints() != null ? user.getPoints() : 0;
        Long currentLevelId = user.getMemberLevelId() != null ? user.getMemberLevelId() : 0L;

        // 获取当前等级信息
        MembershipLevel currentLevel = null;
        String oldLevelName = "非会员";
        if (currentLevelId > 0) {
            currentLevel = membershipLevelMapper.selectById(currentLevelId);
            if (currentLevel != null) {
                oldLevelName = currentLevel.getName();
            }
        }

        // 查询所有等级，按 level 升序
        List<MembershipLevel> allLevels = listAll();

        // 找到当前积分能达到的最高等级（threshold <= currentPoints 且 threshold > 0）
        MembershipLevel bestLevel = null;
        for (MembershipLevel level : allLevels) {
            if (level.getThreshold() <= currentPoints) {
                bestLevel = level;
            }
        }

        if (bestLevel == null || bestLevel.getId().equals(currentLevelId)) {
            throw new BusinessException("暂无可升级的会员等级，当前积分：" + currentPoints);
        }

        // 如果当前等级更高或相同，不需要升级
        if (currentLevel != null && currentLevel.getLevel() >= bestLevel.getLevel()) {
            throw new BusinessException("已是最适合的会员等级");
        }

        // 升级
        user.setMemberLevelId(bestLevel.getId());
        userMapper.updateById(user);

        return new UpgradeVO(oldLevelName, bestLevel.getName(), currentPoints);
    }

    @Override
    public java.math.BigDecimal getCurrentUserDiscount() {
        Long userId = com.petshop.security.UserContext.getUserId();
        if (userId == null) {
            return java.math.BigDecimal.ONE;
        }
        User user = userMapper.selectById(userId);
        if (user == null || user.getMemberLevelId() == null || user.getMemberLevelId() <= 0) {
            return java.math.BigDecimal.ONE;
        }
        MembershipLevel level = membershipLevelMapper.selectById(user.getMemberLevelId());
        if (level == null || level.getDiscount() == null) {
            return java.math.BigDecimal.ONE;
        }
        return level.getDiscount();
    }

    @Override
    public String getCurrentUserLevelName() {
        Long userId = com.petshop.security.UserContext.getUserId();
        if (userId == null) return null;
        User user = userMapper.selectById(userId);
        if (user == null || user.getMemberLevelId() == null || user.getMemberLevelId() <= 0) return null;
        MembershipLevel level = membershipLevelMapper.selectById(user.getMemberLevelId());
        if (level == null) return null;
        return level.getName();
    }
}
