package com.petshop.controller;

import com.petshop.common.BusinessException;
import com.petshop.common.Result;
import com.petshop.security.RequireLogin;
import com.petshop.security.UserContext;
import com.petshop.user.model.dto.UpdateUserDTO;
import com.petshop.user.model.vo.UserVO;
import com.petshop.user.service.UserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 用户接口：查看/修改个人信息（需登录）。
 */
@Api(tags = "02-用户信息")
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @ApiOperation("获取当前登录用户信息")
    @RequireLogin
    @GetMapping("/me")
    public Result<UserVO> me() {
        Long userId = UserContext.getUserId();
        UserVO vo = userService.getCurrentUser(userId);
        return Result.success(vo);
    }

    @ApiOperation("修改当前用户信息")
    @RequireLogin
    @PutMapping("/me")
    public Result<Void> updateMe(@RequestBody UpdateUserDTO dto) {
        Long userId = UserContext.getUserId();
        userService.updateCurrentUser(userId, dto);
        return Result.success();
    }

    @ApiOperation("账户充值（模拟支付）")
    @RequireLogin
    @PostMapping("/recharge")
    public Result<Map<String, Object>> recharge(@RequestBody Map<String, Object> body) {
        Long userId = UserContext.getUserId();
        Object amountObj = body.get("amount");
        if (amountObj == null) {
            throw new BusinessException("请输入充值金额");
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountObj.toString());
        } catch (NumberFormatException e) {
            throw new BusinessException("金额格式不正确");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("充值金额必须大于 0");
        }
        if (amount.compareTo(new BigDecimal("999999")) > 0) {
            throw new BusinessException("单次充值金额不能超过 999,999");
        }
        BigDecimal newBalance = userService.recharge(userId, amount);
        return Result.success(Map.of("balance", newBalance));
    }
}
