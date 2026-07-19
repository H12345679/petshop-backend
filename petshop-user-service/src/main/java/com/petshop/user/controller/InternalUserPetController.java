package com.petshop.user.controller;

import com.petshop.user.service.UserPetService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 服务间内部接口（供推荐服务经 Feign 调用，见 common 的 UserPetClient）。
 * <p>
 * /internal/** 不在网关路由表内，外部前端无法经网关访问。
 */
@Hidden
@RestController
@RequestMapping("/internal/user-pets")
public class InternalUserPetController {

    @Autowired
    private UserPetService userPetService;

    /** 用户宠物档案映射出的商品标签名集合。 */
    @GetMapping("/{userId}/tag-names")
    public List<String> petTagNames(@PathVariable("userId") Long userId) {
        return userPetService.petTagNames(userId);
    }

    /** 用户养的物种集合（1猫 2狗 3兔 4鸟）。 */
    @GetMapping("/{userId}/species")
    public List<Integer> petSpecies(@PathVariable("userId") Long userId) {
        return userPetService.petSpecies(userId);
    }
}
