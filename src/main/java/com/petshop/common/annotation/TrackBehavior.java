package com.petshop.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 记录用户行为的埋点注解。
 * 可以直接加在 Controller 方法上。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TrackBehavior {

    /**
     * 行为类型：1浏览 2收藏 3加购 4购买
     */
    int type();

    /**
     * Spring EL 表达式，用于从方法的参数列表中动态提取商品 ID (productId)。
     * 示例：
     * 如果参数是 Long id，则填 "#id"
     * 如果参数是 CartItem cartItem，则填 "#cartItem.productId"
     */
    String productIdSpEL() default "";
}
