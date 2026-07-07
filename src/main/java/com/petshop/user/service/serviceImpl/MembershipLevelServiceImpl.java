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

        MembershipLevel currentLevel = currentLevelId > 0
                ? membershipLevelMapper.selectById(currentLevelId) : null;
        String oldLevelName = currentLevel != null ? currentLevel.getName() : "非会员";

        MembershipLevel bestLevel = findBestLevel(listAll(), currentPoints);

        if (bestLevel == null || bestLevel.getId().equals(currentLevelId)) {
            throw new BusinessException("暂无可升级的会员等级，当前积分：" + currentPoints);
        }
        if (currentLevel != null && currentLevel.getLevel() >= bestLevel.getLevel()) {
            throw new BusinessException("已是最适合的会员等级");
        }

        user.setMemberLevelId(bestLevel.getId());
        userMapper.updateById(user);

        return new UpgradeVO(oldLevelName, bestLevel.getName(), currentPoints);
    }

    private MembershipLevel findBestLevel(List<MembershipLevel> allLevels, int currentPoints) {
        MembershipLevel bestLevel = null;
        for (MembershipLevel level : allLevels) {
            if (level.getThreshold() <= currentPoints) {
                bestLevel = level;
            }
        }
        return bestLevel;
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
