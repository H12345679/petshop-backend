package com.petshop.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;

/**
 * 统一分页返回对象。把 MyBatis-Plus 的 IPage 转成精简的分页结构。
 */
@Data
public class PageResult<T> {

    private long total;
    private long pages;
    private long current;
    private long size;
    private List<T> records;

    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> pr = new PageResult<>();
        pr.setTotal(page.getTotal());
        pr.setPages(page.getPages());
        pr.setCurrent(page.getCurrent());
        pr.setSize(page.getSize());
        pr.setRecords(page.getRecords());
        return pr;
    }
}
