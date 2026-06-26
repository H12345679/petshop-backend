package com.petshop.user.service.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.petshop.common.BusinessException;
import com.petshop.security.JwtUtil;
import com.petshop.user.entity.User;
import com.petshop.user.mapper.UserMapper;
import com.petshop.user.model.dto.LoginDTO;
import com.petshop.user.model.dto.RegisterDTO;
import com.petshop.user.model.dto.UpdateUserDTO;
import com.petshop.user.model.vo.LoginVO;
import com.petshop.user.model.vo.UserVO;
import com.petshop.user.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public UserVO register(RegisterDTO dto) {
        // 检查用户名唯一性
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("username", dto.getUsername());
        if (userMapper.selectCount(qw) > 0) {
            throw new BusinessException("用户名已存在");
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setRole("USER");
        user.setMemberLevelId(0L);
        user.setBalance(BigDecimal.ZERO);
        user.setPoints(0);
        user.setStatus(1);

        userMapper.insert(user);
        return toVO(user);
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        // 按用户名查询
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("username", dto.getUsername());
        User user = userMapper.selectOne(qw);
        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }

        // 校验密码
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        // 检查账号是否被禁用
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用，请联系客服");
        }

        // 签发 JWT
        String token = jwtUtil.createToken(user.getId(), user.getUsername(), user.getRole());

        // 更新最后登录时间
        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);

        // 组装返回
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUser(toVO(user));
        return vo;
    }

    @Override
    public UserVO getCurrentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return toVO(user);
    }

    @Override
    public void updateCurrentUser(Long userId, UpdateUserDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 仅更新非空字段
        if (dto.getNickname() != null) {
            user.setNickname(dto.getNickname());
        }
        if (dto.getAvatar() != null) {
            user.setAvatar(dto.getAvatar());
        }
        if (dto.getPhone() != null) {
            user.setPhone(dto.getPhone());
        }
        if (dto.getEmail() != null) {
            user.setEmail(dto.getEmail());
        }
        if (dto.getGender() != null) {
            user.setGender(dto.getGender());
        }

        userMapper.updateById(user);
    }

    /** User 实体 -> UserVO */
    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }
}
