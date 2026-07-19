package com.petshop.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.petshop.shop.entity.ShopCustomer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ShopCustomerMapper extends BaseMapper<ShopCustomer> {
    
    @Update("INSERT INTO shop_customer(shop_id, user_id, last_purchase_time) " +
            "VALUES(#{shopId}, #{userId}, NOW()) " +
            "ON DUPLICATE KEY UPDATE last_purchase_time = NOW(), deleted = 0")
    void insertOrUpdatePurchaseTime(@Param("shopId") Long shopId, @Param("userId") Long userId);
}
