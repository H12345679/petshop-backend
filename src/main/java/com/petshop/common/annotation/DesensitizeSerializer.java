package com.petshop.common.annotation;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;

import java.io.IOException;
import java.util.Objects;

/**
 * 自定义 Jackson 数据脱敏序列化器
 * 实现 {@link JsonSerializer} 来执行实际的字符串修改。
 * 实现 {@link ContextualSerializer} 来动态获取字段上的 {@link Desensitize} 注解中的脱敏类型。
 */
public class DesensitizeSerializer extends JsonSerializer<String> implements ContextualSerializer {

    /**
     * 当前使用的脱敏类型
     */
    private DesensitizeType type;

    public DesensitizeSerializer() {}

    public DesensitizeSerializer(DesensitizeType type) {
        this.type = type;
    }

    /**
     * 将对象进行序列化处理，通过匹配的脱敏规则替换实际字符串
     *
     * @param value       原字符串值
     * @param gen         JSON 生成器
     * @param serializers 序列化提供者
     * @throws IOException IO 异常
     */
    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null || value.trim().isEmpty()) {
            gen.writeString(value);
            return;
        }
        gen.writeString(type.getDesensitizeFunction().apply(value));
    }

    /**
     * 获取属性上的注解属性，并根据此属性创建带有特定脱敏类型的序列化器对象
     *
     * @param prov     序列化提供者
     * @param property 当前序列化的 Bean 属性
     * @return 匹配规则的 JsonSerializer 对象
     * @throws JsonMappingException JSON 映射异常
     */
    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property) throws JsonMappingException {
        if (property != null) {
            if (Objects.equals(property.getType().getRawClass(), String.class)) {
                Desensitize annotation = property.getAnnotation(Desensitize.class);
                if (annotation == null) {
                    annotation = property.getContextAnnotation(Desensitize.class);
                }
                if (annotation != null) {
                    return new DesensitizeSerializer(annotation.value());
                }
            }
            return prov.findValueSerializer(property.getType(), property);
        }
        return prov.findNullValueSerializer(null);
    }
}
