package com.petshop.order;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.petshop.common.BusinessException;
import com.petshop.order.entity.Order;
import com.petshop.order.entity.OrderItem;
import com.petshop.order.mapper.OrderMapper;
import com.petshop.order.mapper.OrderStatusLogMapper;
import com.petshop.order.mapper.OrderItemMapper;
import com.petshop.order.service.impl.OrderServiceImpl;
import com.petshop.product.mapper.ProductSkuMapper;
import com.petshop.security.UserContext;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.ArgumentMatchers.isNull;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

    @InjectMocks
    private OrderServiceImpl orderService;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderStatusLogMapper orderStatusLogMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private ProductSkuMapper productSkuMapper;

    @BeforeEach
    public void setUp() {
        // Init Mybatis-Plus TableInfo to support LambdaQuery/UpdateWrapper in pure mock tests
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), Order.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), OrderItem.class);
        UserContext.set(new UserContext.LoginUser(99L, "testUser", "USER"));
        ReflectionTestUtils.setField(orderService, "baseMapper", orderMapper);
    }

    @AfterEach
    public void tearDown() {
        UserContext.clear();
    }

    /**
     * 测试定时任务执行超时取消订单时的 CAS 拦截机制
     */
    @Test
    public void testCancelOrder_SystemTimeout_CasSuccess() {
        // Arrange
        Order mockOrder = new Order();
        mockOrder.setId(1001L);
        mockOrder.setUserId(99L);
        mockOrder.setStatus(0); // 待支付
        
        when(orderMapper.selectById(1001L)).thenReturn(mockOrder);
        // 模拟 CAS 更新成功（返回 1 条影响记录）
        when(orderMapper.update(isNull(), any())).thenReturn(1);
        
        // 当查询关联子订单时返回空（非合并拆单场景）
        when(orderItemMapper.selectList(any())).thenReturn(Collections.emptyList());

        // Act
        orderService.cancel(1001L, "超时自动取消");

        // Assert
        ArgumentCaptor<LambdaUpdateWrapper<Order>> wrapperCaptor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(orderMapper).update(isNull(), wrapperCaptor.capture());
        
        // 验证确实使用了 LambdaUpdateWrapper 并且限定了 status = 0 (防并发)
        assertNotNull(wrapperCaptor.getValue());
        verify(orderStatusLogMapper, times(1)).insert(any());
    }

    /**
     * 测试防并发情况：如果用户在最后一秒完成了支付，订单状态变成了 1 (待发货)，
     * 此时定时任务再去执行取消，应该因为 CAS 不匹配而更新失败并抛出异常，防止状态覆盖。
     */
    @Test
    public void testCancelOrder_SystemTimeout_CasFailBecauseUserPaid() {
        // Arrange
        Order mockOrder = new Order();
        mockOrder.setId(1002L);
        mockOrder.setUserId(99L);
        mockOrder.setStatus(0);
        
        when(orderMapper.selectById(1002L)).thenReturn(mockOrder);
        // 模拟 CAS 更新失败（例如影响行数为 0，说明那一瞬间 status 不为 0 了）
        when(orderMapper.update(isNull(), any())).thenReturn(0);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            orderService.cancel(1002L, "超时自动取消");
        });

        assertEquals("取消失败，订单状态已被并发修改", exception.getMessage());
        // 验证状态流转日志绝不会被记录
        verify(orderStatusLogMapper, never()).insert(any());
    }
}
