package com.petshop.content;

import com.petshop.common.BusinessException;
import com.petshop.content.dto.MessageSendDTO;
import com.petshop.content.service.impl.MessageServiceImpl;
import com.petshop.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
public class MessageServiceTest {

    @InjectMocks
    private MessageServiceImpl messageService;

    @BeforeEach
    public void setUp() {
        // 设置当前用户上下文为商家
        UserContext.set(new UserContext.LoginUser(1L, "merchantUser", "MERCHANT"));
    }

    @AfterEach
    public void tearDown() {
        // 清理线程变量
        UserContext.clear();
    }

    @Test
    public void testSendMessage_MerchantSendsSystemNotice_ThrowsException() {
        // Arrange
        MessageSendDTO dto = new MessageSendDTO();
        dto.setType(1); // 1 = System Notice

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            messageService.sendMessage(dto);
        });

        assertEquals("商家无法发送系统通知", exception.getMessage());
    }
}
