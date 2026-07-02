package com.petshop.user;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.petshop.common.BusinessException;
import com.petshop.security.JwtUtil;
import com.petshop.user.entity.User;
import com.petshop.user.mapper.UserMapper;
import com.petshop.user.model.dto.LoginDTO;
import com.petshop.user.model.dto.RegisterDTO;
import com.petshop.user.service.serviceImpl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @InjectMocks
    private UserServiceImpl userService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Test
    public void testRegister_UsernameExists_ThrowsException() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("testuser");
        dto.setPassword("123456");

        // 模拟数据库中已存在同名用户
        when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            userService.register(dto);
        });
        assertEquals("用户名已存在", exception.getMessage());
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    public void testLogin_InvalidPassword_ThrowsException() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("testuser");
        dto.setPassword("wrongpassword");

        User mockUser = new User();
        mockUser.setUsername("testuser");
        mockUser.setPassword("encoded_password");

        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(mockUser);
        when(passwordEncoder.matches("wrongpassword", "encoded_password")).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            userService.login(dto);
        });
        assertEquals("用户名或密码错误", exception.getMessage());
    }
}
