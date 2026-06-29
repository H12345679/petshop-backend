package com.petshop.user.controller;

import com.petshop.common.Result;
import com.petshop.user.model.dto.EmailLoginDTO;
import com.petshop.user.model.dto.LoginDTO;
import com.petshop.user.model.dto.OAuthLoginDTO;
import com.petshop.user.model.dto.RegisterDTO;
import com.petshop.user.model.dto.SendCodeDTO;
import com.petshop.user.model.vo.LoginVO;
import com.petshop.user.model.vo.OAuthLoginVO;
import com.petshop.user.model.vo.UserVO;
import com.petshop.user.service.EmailAuthService;
import com.petshop.user.service.UserOauthService;
import com.petshop.user.service.UserService;
import com.petshop.log.annotation.LogOperation;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 认证接口：注册 / 登录 / OAuth / 邮箱验证码（公开接口）。
 */
@Api(tags = "01-用户认证")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserOauthService userOauthService;

    @Autowired
    private EmailAuthService emailAuthService;

    @ApiOperation("用户注册")
    @LogOperation("用户注册")
    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterDTO dto) {
        UserVO vo = userService.register(dto);
        return Result.success(vo);
    }

    @ApiOperation("用户登录")
    @LogOperation("用户登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        LoginVO vo = userService.login(dto);
        return Result.success(vo);
    }

    @ApiOperation("第三方 OAuth 登录/绑定")
    @PostMapping("/oauth/login")
    public Result<OAuthLoginVO> oauthLogin(@Valid @RequestBody OAuthLoginDTO dto) {
        OAuthLoginVO vo = userOauthService.login(dto);
        return Result.success(vo);
    }

    @ApiOperation("发送邮箱验证码")
    @PostMapping("/email/send-code")
    public Result<Void> sendEmailCode(@Valid @RequestBody SendCodeDTO dto) {
        emailAuthService.sendCode(dto.getEmail());
        return Result.success(null);
    }

    @ApiOperation("邮箱验证码登录")
    @PostMapping("/email/login")
    public Result<LoginVO> emailLogin(@Valid @RequestBody EmailLoginDTO dto) {
        LoginVO vo = emailAuthService.login(dto);
        return Result.success(vo);
    }
}
