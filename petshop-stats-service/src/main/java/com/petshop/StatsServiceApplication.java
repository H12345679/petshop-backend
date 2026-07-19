package com.petshop;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 统计服务：经营统计报表 + 系统操作日志查询
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.petshop")
@MapperScan("com.petshop.**.mapper")
public class StatsServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(StatsServiceApplication.class, args);
    }
}