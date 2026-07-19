package com.petshop;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 推荐服务：用户行为消费(MQ)/画像/多路召回排序
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.petshop")
@MapperScan("com.petshop.**.mapper")
@EnableScheduling
public class RecommendServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RecommendServiceApplication.class, args);
    }
}