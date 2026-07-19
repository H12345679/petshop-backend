package com.petshop.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.petshop.order.entity.Review;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ReviewMapper extends BaseMapper<Review> {

    /** 恢复已逻辑删除的评价 */
    @Update("UPDATE review SET deleted = 0 WHERE id = #{id}")
    int restoreById(@Param("id") Long id);

    /** 查询已删除评价（含动态条件，分页由 MP 拦截器自动处理） */
    Page<Review> selectManageWithDeleted(Page<Review> page,
                                         @Param("shopIds") List<Long> shopIds,
                                         @Param("productId") Long productId,
                                         @Param("rating") Integer rating,
                                         @Param("hasReply") Integer hasReply);

    /** 统计已删除评价数量 */
    long countManageWithDeleted(@Param("shopIds") List<Long> shopIds,
                                @Param("productId") Long productId,
                                @Param("rating") Integer rating,
                                @Param("hasReply") Integer hasReply);
}
