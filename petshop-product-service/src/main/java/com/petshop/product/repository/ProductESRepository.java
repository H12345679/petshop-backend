package com.petshop.product.repository;

import com.petshop.product.entity.ProductES;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ProductESRepository extends ElasticsearchRepository<ProductES, Long> {
}
