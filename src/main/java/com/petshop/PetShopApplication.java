package com.petshop;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 宠物商店后端启动类。
 * 各模块的 Mapper 放在 com.petshop.<模块>.mapper 包下即可被扫描到。
 */
@SpringBootApplication
@MapperScan("com.petshop.**.mapper")
public class PetShopApplication {
    public static void main(String[] args) {
        SpringApplication.run(PetShopApplication.class, args);
    }
}
