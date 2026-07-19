package com.petshop.api.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * 用户服务 Feign 客户端（服务间内部调用）。
 * <p>
 * 推荐服务在做「宠物画像召回」时，跨服务获取用户宠物档案映射出的标签与物种。
 * 对应实现见 user 服务的 InternalUserPetController。
 */
@FeignClient(name = "petshop-user-service", contextId = "userPetClient")
public interface UserPetClient {

    /** 用户宠物档案映射出的商品标签名集合（无档案返回空列表）。 */
    @GetMapping("/internal/user-pets/{userId}/tag-names")
    List<String> petTagNames(@PathVariable("userId") Long userId);

    /** 用户养的物种集合（1猫 2狗 3兔 4鸟），用于人群召回与冲突过滤。 */
    @GetMapping("/internal/user-pets/{userId}/species")
    List<Integer> petSpecies(@PathVariable("userId") Long userId);
}
