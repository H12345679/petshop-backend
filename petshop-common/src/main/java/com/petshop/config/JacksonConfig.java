package com.petshop.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigInteger;

/**
 * 雪花 ID 为 Long(19 位)，超出 JS Number 安全整数 2^53，
 * 若直接以 JSON number 返回，前端解析后末几位会被舍位，导致 id 与库不一致。
 * 这里全局把 Long / long / BigInteger 序列化为字符串，前端按字符串接收即可。
 * 通过 Customizer 增量定制，保留 application.yml 中的日期格式等其它 Jackson 配置。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> builder
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(Long.TYPE, ToStringSerializer.instance)
                .serializerByType(BigInteger.class, ToStringSerializer.instance);
    }
}
