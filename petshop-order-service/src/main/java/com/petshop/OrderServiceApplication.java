package com.petshop;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 订单服务：购物车/订单/退款/评价/优惠券 + 订单超时取消(MQ死信)
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.petshop")
@MapperScan("com.petshop.**.mapper")
@EnableScheduling
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}