package com.petshop.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.petshop.product.entity.ProductTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface ProductTagMapper extends BaseMapper<ProductTag> {

    @Select("SELECT t.name FROM tag t JOIN product_tag pt ON t.id = pt.tag_id WHERE pt.product_id = #{productId} AND t.deleted = 0")
    List<String> selectTagNamesByProductId(@Param("productId") Long productId);

    @Select("SELECT t.id FROM tag t JOIN product_tag pt ON t.id = pt.tag_id WHERE pt.product_id = #{productId} AND t.deleted = 0")
    List<Long> selectTagIdsByProductId(@Param("productId") Long productId);
    
    @Select("<script>"
          + "SELECT DISTINCT pt.product_id FROM product_tag pt WHERE pt.tag_id IN "
          + "<foreach collection='tagIds' item='tagId' open='(' separator=',' close=')'>"
          + "#{tagId}"
          + "</foreach>"
          + "</script>")
    List<Long> selectProductIdsByTagIds(@Param("tagIds") List<Long> tagIds);
}
