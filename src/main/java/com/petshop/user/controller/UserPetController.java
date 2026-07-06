package com.petshop.user.controller;

import com.petshop.common.Result;
import com.petshop.security.RequireLogin;
import com.petshop.user.entity.UserPet;
import com.petshop.user.service.UserPetService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户宠物档案接口（推荐深化 P0：注册/个人中心维护，档案标签参与首页推荐）。
 */
@Api(tags = "03-用户宠物档案")
@RestController
@RequestMapping("/api/user/pets")
public class UserPetController {

    @Autowired
    private UserPetService userPetService;

    @ApiOperation("我的宠物列表")
    @RequireLogin
    @GetMapping
    public Result<List<UserPet>> myPets() {
        return Result.success(userPetService.myPets());
    }

    @ApiOperation("新增宠物档案")
    @RequireLogin
    @PostMapping
    public Result<Void> add(@RequestBody UserPet pet) {
        userPetService.addPet(pet);
        return Result.success();
    }

    @ApiOperation("修改宠物档案")
    @RequireLogin
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody UserPet pet) {
        userPetService.updatePet(id, pet);
        return Result.success();
    }

    @ApiOperation("删除宠物档案")
    @RequireLogin
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userPetService.deletePet(id);
        return Result.success();
    }
}
