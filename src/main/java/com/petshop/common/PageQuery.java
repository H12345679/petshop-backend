package com.petshop.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一分页请求对象。
 * <p>
 * 各模块的分页查询 DTO 继承此类，添加业务查询条件即可，例如：
 * <pre>
 * public class ProductPageQuery extends PageQuery {
 *     private String keyword;
 *     private Long categoryId;
 * }
 * </pre>
 * Controller 中直接作为方法参数接收前端 query 参数。
 */
@Data
public class PageQuery implements Serializable {

    /** 页码，从 1 开始，默认 1 */
    @Schema(description = "页码（从1开始）", example = "1")
    private long current = 1;

    /** 每页条数，默认 10，最大 100 */
    @Schema(description = "每页条数", example = "10")
    private long size = 10;

    /** 最大允许每页条数 */
    private static final long MAX_SIZE = 100;

    /**
     * 转为 MyBatis-Plus 的 Page 对象，自动对 size 做上限校验。
     */
    public <T> Page<T> toPage() {
        long s = this.size > MAX_SIZE ? MAX_SIZE : (this.size < 1 ? 10 : this.size);
        long c = this.current < 1 ? 1 : this.current;
        return new Page<>(c, s);
    }
}
