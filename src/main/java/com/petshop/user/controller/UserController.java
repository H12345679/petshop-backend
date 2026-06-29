package com.petshop.user.controller;

import com.petshop.common.PageResult;
import com.petshop.common.Result;
import com.petshop.security.RequireLogin;
import com.petshop.security.RequireRole;
import com.petshop.security.UserContext;
import com.petshop.user.model.dto.ChangePasswordDTO;
import com.petshop.user.model.dto.UpdateUserDTO;
import com.petshop.user.model.dto.UserRoleDTO;
import com.petshop.user.model.dto.UserStatusDTO;
import com.petshop.user.model.vo.UserManageVO;
import com.petshop.user.model.vo.UserVO;
import com.petshop.user.service.UserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

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

    @ApiOperation("修改当前用户密码")
    @RequireLogin
    @PutMapping("/me/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        Long userId = UserContext.getUserId();
        userService.changePassword(userId, dto);
        return Result.success();
    }

    @ApiOperation("后台用户管理列表")
    @RequireRole({"ADMIN"})
    @GetMapping("/manage")
    public Result<PageResult<UserManageVO>> manageList(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String username) {
        PageResult<UserManageVO> result = userService.manageList(current, size, username);
        return Result.success(result);
    }

    @ApiOperation("后台启用/禁用用户")
    @RequireRole({"ADMIN"})
    @PutMapping("/{id}/status")
    public Result<Void> updateUserStatus(
            @PathVariable Long id,
            @Valid @RequestBody UserStatusDTO dto) {
        userService.updateUserStatus(id, dto);
        return Result.success();
    }

    @ApiOperation("后台授予/变更用户角色")
    @RequireRole({"ADMIN"})
    @PutMapping("/{id}/role")
    public Result<Void> updateUserRole(
            @PathVariable Long id,
            @Valid @RequestBody UserRoleDTO dto) {
        userService.updateUserRole(id, dto);
        return Result.success();
    }
}
