package com.petshop.api.client;

import com.petshop.product.entity.Product;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 推荐服务 Feign 客户端（服务间内部调用）。
 * <p>
 * 商品服务的首页「智能推荐」板块跨服务获取个性化排序结果。
 * 对应实现见 recommend 服务的 InternalRecommendController。
 */
@FeignClient(name = "petshop-recommend-service", contextId = "recommendClient")
public interface RecommendClient {

    /** 为指定用户生成个性化推荐商品 TopN（含推荐理由 recommendReason）。 */
    @GetMapping("/internal/recommend/rank")
    List<Product> rankForUser(@RequestParam("userId") Long userId, @RequestParam("n") int n);
}
