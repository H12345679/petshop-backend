package com.petshop.product.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 首页动态楼层视图对象。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HomeSectionVO {
    /** 楼层类型标识，如 "CF", "HOT", "NEW", "RECOMMEND" */
    private String tag;
    /** 楼层展示标题，如 "🛍️ 大家都在买" */
    private String title;
    /** 该楼层包含的商品列表 */
    private List<Product> list;
}
