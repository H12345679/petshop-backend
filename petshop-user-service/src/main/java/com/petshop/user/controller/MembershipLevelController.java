package com.petshop.user.controller;

import com.petshop.common.Result;
import com.petshop.security.RequireLogin;
import com.petshop.security.UserContext;
import com.petshop.user.entity.MembershipLevel;
import com.petshop.user.model.vo.UpgradeVO;
import com.petshop.user.service.MembershipLevelService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会员等级接口（公开接口）。
 */
@Tag(name = "04-会员等级")
@RestController
@RequestMapping("/api/membership")
public class MembershipLevelController {

    @Autowired
    private MembershipLevelService membershipLevelService;

    @Operation(summary = "会员等级列表查询")
    @GetMapping("/levels")
    public Result<List<MembershipLevel>> listLevels() {
        List<MembershipLevel> list = membershipLevelService.listAll();
        return Result.success(list);
    }

    @Operation(summary = "会员等级升级（根据积分自动匹配）")
    @RequireLogin
    @PostMapping("/upgrade")
    public Result<UpgradeVO> upgrade() {
        Long userId = UserContext.getUserId();
        UpgradeVO vo = membershipLevelService.upgrade(userId);
        return Result.success(vo);
    }
}
