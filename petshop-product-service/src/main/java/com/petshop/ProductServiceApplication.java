package com.petshop;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 商品服务：商品/分类/首页/ES搜索/文件上传
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.petshop")
@MapperScan("com.petshop.**.mapper")
public class ProductServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}