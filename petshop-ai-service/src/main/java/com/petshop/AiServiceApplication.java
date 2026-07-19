package com.petshop;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * AI 服务：智能客服对话（SSE 流式）
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.petshop")
@MapperScan("com.petshop.**.mapper")
public class AiServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}