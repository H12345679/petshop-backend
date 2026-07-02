package com.petshop.product;

import com.petshop.common.BusinessException;
import com.petshop.product.entity.Product;
import com.petshop.product.mapper.ProductMapper;
import com.petshop.product.service.impl.ProductServiceImpl;
import com.petshop.security.OwnershipChecker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductServiceTest {

    @InjectMocks
    private ProductServiceImpl productService;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private OwnershipChecker ownershipChecker;

    @Test
    public void testDeleteProduct_OwnershipFails_ThrowsException() {
        // 模拟当前登录商家操作不属于他的商品 (抛出异常)
        doThrow(new BusinessException("无权操作该商品")).when(ownershipChecker).assertProductOwned(200L);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            productService.deleteProduct(200L);
        });

        assertEquals("无权操作该商品", exception.getMessage());
        // 验证没有执行删除
        verify(productMapper, never()).deleteById(anyLong());
    }
}
