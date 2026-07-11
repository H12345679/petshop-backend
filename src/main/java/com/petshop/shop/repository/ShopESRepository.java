package com.petshop.shop.repository;

import com.petshop.shop.entity.ShopES;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ShopESRepository extends ElasticsearchRepository<ShopES, Long> {
}
