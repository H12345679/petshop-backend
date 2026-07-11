package com.petshop.content.repository;

import com.petshop.content.entity.VideoES;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface VideoESRepository extends ElasticsearchRepository<VideoES, Long> {
}
