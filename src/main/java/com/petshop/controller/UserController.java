package com.petshop.controller;

import com.petshop.common.Result;
import com.petshop.security.RequireLogin;
import com.petshop.security.UserContext;
import com.petshop.user.model.dto.UpdateUserDTO;
import com.petshop.user.model.vo.UserVO;
import com.petshop.user.service.UserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
