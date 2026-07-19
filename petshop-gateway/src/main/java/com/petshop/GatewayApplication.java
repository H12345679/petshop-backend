package com.petshop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API 网关：前端唯一入口（8088，与原单体端口一致，前端代理零改动）。
 * 路由规则见 application.yml；JWT 鉴权仍由各业务服务的拦截器完成，网关只透传 Authorization 头。
 */
@SpringBootApplication
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
