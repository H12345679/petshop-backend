package com.petshop.common.annotation;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.*;

/**
 * 敏感数据脱敏注解
 * 用于标记需要进行数据脱敏的字符串字段，在 JSON 序列化时自动触发拦截并进行内容脱敏处理。
 * 注意：该注解需要配合 Jackson 的 {@link DesensitizeSerializer} 使用。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@JacksonAnnotationsInside
@JsonSerialize(using = DesensitizeSerializer.class)
public @interface Desensitize {
    
    /**
     * @return 脱敏策略类型
     */
    DesensitizeType value();
}
