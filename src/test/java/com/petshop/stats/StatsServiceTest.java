package com.petshop.stats;

import com.petshop.order.mapper.OrderMapper;
import com.petshop.product.mapper.ProductMapper;
import com.petshop.security.OwnershipChecker;
import com.petshop.stats.service.impl.StatsServiceImpl;
import com.petshop.user.mapper.MembershipLevelMapper;
import com.petshop.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class StatsServiceTest {

    @InjectMocks
    private StatsServiceImpl statsService;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private MembershipLevelMapper membershipLevelMapper;

    @Mock
    private OwnershipChecker ownershipChecker;

    @Test
    public void testGetKpi_EmptyShops() {
        when(ownershipChecker.myShopIds()).thenReturn(Collections.emptyList());
        
        Map<String, Object> kpi = statsService.getKpi();
        
        assertEquals(BigDecimal.ZERO, kpi.get("todayRevenue"));
        assertEquals(0, kpi.get("todayOrders"));
        assertEquals(0L, kpi.get("totalUsers"));
        assertEquals(0L, kpi.get("activeProducts"));
    }
}
