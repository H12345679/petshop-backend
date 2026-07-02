package com.petshop.user.service.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.petshop.common.BusinessException;
import com.petshop.common.PageResult;
import com.petshop.security.JwtUtil;
import com.petshop.user.entity.User;
import com.petshop.user.mapper.UserMapper;
import com.petshop.user.model.dto.ChangePasswordDTO;
import com.petshop.user.model.dto.LoginDTO;
import com.petshop.user.model.dto.RegisterDTO;
import com.petshop.user.model.dto.UpdateUserDTO;
import com.petshop.user.model.dto.UserRoleDTO;
import com.petshop.user.model.dto.UserStatusDTO;
import com.petshop.user.model.vo.LoginVO;
import com.petshop.user.model.vo.UserManageVO;
import com.petshop.user.model.vo.UserVO;
import com.petshop.user.service.UserService;
import com.petshop.security.OwnershipChecker;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OwnershipChecker ownershipChecker;

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

    @Override
    public void changePassword(Long userId, ChangePasswordDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 校验旧密码
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(400, "旧密码错误");
        }

        // 加密新密码并更新
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userMapper.updateById(user);
    }

    @Override
    public BigDecimal recharge(Long userId, BigDecimal amount) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        BigDecimal current = user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;
        BigDecimal newBalance = current.add(amount);
        user.setBalance(newBalance);
        userMapper.updateById(user);
        return newBalance;
    }

    @Override
    public PageResult<UserManageVO> manageList(int current, int size, String username, String role, Long memberLevelId, Integer status) {
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("deleted", 0);
        if (username != null && !username.trim().isEmpty()) {
            qw.like("username", username.trim());
        }
        if (role != null && !role.trim().isEmpty()) {
            qw.eq("role", role.trim());
        }
        if (memberLevelId != null) {
            qw.eq("member_level_id", memberLevelId);
        }
        if (status != null) {
            qw.eq("status", status);
        }
        qw.orderByDesc("create_time");

        Page<User> page = new Page<>(current, size);
        IPage<User> result = userMapper.selectPage(page, qw);

        List<UserManageVO> records = result.getRecords().stream()
                .map(this::toManageVO)
                .collect(Collectors.toList());

        PageResult<UserManageVO> pr = new PageResult<>();
        pr.setTotal(result.getTotal());
        pr.setPages(result.getPages());
        pr.setCurrent(result.getCurrent());
        pr.setSize(result.getSize());
        pr.setRecords(records);
        return pr;
    }

    @Override
    public PageResult<UserManageVO> customerList(int current, int size, String username) {
        List<Long> shopIds = ownershipChecker.myShopIds();
        PageResult<UserManageVO> pr = new PageResult<>();
        pr.setTotal(0);
        pr.setPages(0);
        pr.setCurrent(current);
        pr.setSize(size);
        pr.setRecords(new ArrayList<>());
        if (shopIds == null || shopIds.isEmpty()) {
            return pr;
        }

        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("deleted", 0);
        if (username != null && !username.trim().isEmpty()) {
            qw.like("username", username.trim());
        }
        // Use subquery to find users who are customers of the merchant's shops
        qw.inSql("id", "SELECT user_id FROM shop_customer WHERE shop_id IN (" + shopIds.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")");
        qw.orderByDesc("create_time");

        Page<User> page = new Page<>(current, size);
        IPage<User> result = userMapper.selectPage(page, qw);

        List<UserManageVO> records = result.getRecords().stream()
                .map(this::toManageVO)
                .collect(Collectors.toList());

        pr.setTotal(result.getTotal());
        pr.setPages(result.getPages());
        pr.setRecords(records);
        return pr;
    }

    @Override
    public void updateUserStatus(Long userId, UserStatusDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        user.setStatus(dto.getStatus());
        userMapper.updateById(user);
    }

    @Override
    public void updateUserRole(Long userId, UserRoleDTO dto) {
        // 校验角色合法性
        Set<String> validRoles = new HashSet<>(Arrays.asList("USER", "MERCHANT", "ADMIN"));
        if (!validRoles.contains(dto.getRole())) {
            throw new BusinessException(400, "无效的角色类型，仅支持 USER / MERCHANT / ADMIN");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        user.setRole(dto.getRole());
        userMapper.updateById(user);
    }

    /** User 实体 -> UserVO */
    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    /** User 实体 -> UserManageVO */
    private UserManageVO toManageVO(User user) {
        UserManageVO vo = new UserManageVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }
}
