package com.petshop.recommend.controller;

import com.petshop.product.entity.Product;
import com.petshop.recommend.service.RecommendRankService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 服务间内部接口（供商品服务首页板块经 Feign 调用，见 common 的 RecommendClient）。
 * <p>
 * /internal/** 不在网关路由表内，外部前端无法经网关访问。
 */
@Hidden
@RestController
@RequestMapping("/internal/recommend")
public class InternalRecommendController {

    @Autowired
    private RecommendRankService recommendRankService;

    /** 为指定用户生成个性化推荐 TopN（多路召回 + 融合排序，含推荐理由）。 */
    @GetMapping("/rank")
    public List<Product> rankForUser(@RequestParam("userId") Long userId, @RequestParam("n") int n) {
        return recommendRankService.rankForUser(userId, n);
    }
}
