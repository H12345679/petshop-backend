package com.petshop.common.annotation;

import java.util.function.Function;

/**
 * 数据脱敏策略枚举类
 * 包含支持的脱敏类型及对应的脱敏处理逻辑（正则表达式替换）
 */
public enum DesensitizeType {
    
    /**
     * 手机号脱敏
     * (e.g. 13800138000 -> 138****8000)
     */
    PHONE(s -> s.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2")),
    
    /**
     * 邮箱脱敏
     * (e.g. abcdefg@gmail.com -> a****@gmail.com)
     */
    EMAIL(s -> s.replaceAll("(^\\w)[^@]*(@.*$)", "$1****$2"));
    
    /**
     * 执行脱敏的具体函数接口
     */
    private final Function<String, String> desensitizeFunction;

    /**
     * 构造函数
     * @param desensitizeFunction 脱敏函数
     */

    DesensitizeType(Function<String, String> desensitizeFunction) {
        this.desensitizeFunction = desensitizeFunction;
    }

    /**
     * 获取当前枚举项对应的脱敏函数
     * @return 脱敏处理函数
     */
    public Function<String, String> getDesensitizeFunction() {
        return desensitizeFunction;
    }
}
