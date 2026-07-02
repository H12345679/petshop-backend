package com.petshop.map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petshop.map.dto.MapShopQuery;
import com.petshop.map.dto.MapShopResponse;
import com.petshop.map.entity.MapShop;
import com.petshop.map.mapper.MapShopMapper;
import com.petshop.map.service.impl.MapShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MapShopServiceTest {

    @InjectMocks
    private MapShopServiceImpl mapShopService;

    @Mock
    private MapShopMapper mapShopMapper;

    @Test
    public void testFindNearbyShops_NoShopsFound_ReturnsEmptyOrFallback() {
        // Arrange
        MapShopQuery query = new MapShopQuery();
        query.setLongitude(new BigDecimal("116.397128"));
        query.setLatitude(new BigDecimal("39.916527"));
        query.setRadius(new BigDecimal("5.0"));
        query.setLimit(10);

        // 模拟数据库查询不到任何门店
        when(mapShopMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        // Act
        List<MapShopResponse> response = mapShopService.findNearbyShops(query);

        // Assert
        assertTrue(response.isEmpty(), "当没有附近门店时，期望返回空列表（或fallback数据）");
    }
}
