package com.petshop.shop;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.petshop.security.OwnershipChecker;
import com.petshop.security.UserContext;
import com.petshop.shop.entity.Shop;
import com.petshop.shop.mapper.ShopMapper;
import com.petshop.shop.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ShopServiceTest {

    @InjectMocks
    private ShopServiceImpl shopService;

    @Mock
    private ShopMapper shopMapper;

    @Mock
    private OwnershipChecker ownershipChecker;

    @BeforeEach
    public void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Shop.class);
        UserContext.set(new UserContext.LoginUser(99L, "testUser", "USER"));
        ReflectionTestUtils.setField(shopService, "baseMapper", shopMapper);
    }

    @AfterEach
    public void tearDown() {
        UserContext.clear();
    }

    @Test
    public void testCreateShop_AsUser() {
        when(ownershipChecker.isAdmin()).thenReturn(false);
        when(shopMapper.insert(any(Shop.class))).thenReturn(1);
        
        Shop shop = new Shop();
        shop.setName("Test Shop");
        
        shopService.createShop(shop);
        
        assertEquals(99L, shop.getOwnerId());
        assertEquals(1, shop.getStatus());
        verify(shopMapper, times(1)).insert(any(Shop.class));
    }
}
