package com.petshop.user.service.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.petshop.common.BusinessException;
import com.petshop.security.JwtUtil;
import com.petshop.user.entity.User;
import com.petshop.user.mapper.UserMapper;
import com.petshop.user.model.dto.EmailLoginDTO;
import com.petshop.user.model.vo.LoginVO;
import com.petshop.user.model.vo.UserVO;
import com.petshop.user.service.EmailAuthService;
import com.petshop.util.EmailUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 邮箱验证码认证：发送验证码 + 验证码登录（对标 UserOauthService 的第三方登录模式）。
 * 验证码使用 StringRedisTemplate 存入 Redis（纯字符串，无需 JSON 序列化）。
 */
@Service
public class EmailAuthServiceImpl implements EmailAuthService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private EmailUtil emailUtil;

    private static final String CODE_PREFIX = "email:code:";
    private static final String LIMIT_PREFIX = "email:limit:";
    private static final int CODE_TIMEOUT_MINUTES = 5;
    private static final int LIMIT_TIMEOUT_SECONDS = 60;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public void sendCode(String email) {
        // 频率限制：同一邮箱 60 秒内不可重复发送
        String limitKey = LIMIT_PREFIX + email;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(limitKey))) {
            throw new BusinessException("验证码已发送，请60秒后再试");
        }

        // 生成 6 位随机验证码
        String code = String.format("%06d", RANDOM.nextInt(1000000));

        // 存入 Redis（纯字符串，5 分钟有效）
        String codeKey = CODE_PREFIX + email;
        stringRedisTemplate.opsForValue().set(codeKey, code, CODE_TIMEOUT_MINUTES, TimeUnit.MINUTES);

        // 设置频率限制（60 秒）
        stringRedisTemplate.opsForValue().set(limitKey, "1", LIMIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // 发送邮件
        emailUtil.sendVerifyCode(email, code);
    }

    @Override
    public LoginVO login(EmailLoginDTO dto) {
        String email = dto.getEmail();
        String code = dto.getCode();

        // 1. 校验验证码
        String codeKey = CODE_PREFIX + email;
        String storedCode = stringRedisTemplate.opsForValue().get(codeKey);
        if (storedCode == null) {
            throw new BusinessException("验证码已过期，请重新获取");
        }
        if (!code.equals(storedCode)) {
            throw new BusinessException("验证码错误");
        }

        // 2. 验证通过，立即删除验证码（一次性使用）
        stringRedisTemplate.delete(codeKey);

        // 3. 根据邮箱查询用户
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("email", email);
        User user = userMapper.selectOne(qw);

        if (user != null) {
            // 已有账号：检查是否被禁用
            if (user.getStatus() != null && user.getStatus() == 0) {
                throw new BusinessException("账号已被禁用，请联系客服");
            }

            // 更新最后登录时间
            user.setLastLoginTime(LocalDateTime.now());
            userMapper.updateById(user);

            // 签发 JWT
            String token = jwtUtil.createToken(user.getId(), user.getUsername(), user.getRole());
            return buildLoginVO(token, user);
        }

        // 4. 无此用户：自动注册
        User newUser = autoRegister(email);
        String token = jwtUtil.createToken(newUser.getId(), newUser.getUsername(), newUser.getRole());
        return buildLoginVO(token, newUser);
    }

    /** 新用户自动注册：生成唯一用户名、默认昵称，写入 user 表 */
    private User autoRegister(String email) {
        User user = new User();
        user.setEmail(email);
        user.setUsername(generateUniqueUsername(email));
        user.setNickname(email.split("@")[0]);
        user.setPassword("");  // 邮箱注册无密码，填""避免 NOT NULL 报错
        user.setRole("USER");
        user.setMemberLevelId(0L);
        user.setBalance(BigDecimal.ZERO);
        user.setPoints(0);
        user.setStatus(1);
        user.setLastLoginTime(LocalDateTime.now());

        userMapper.insert(user);
        return user;
    }

    /** 生成唯一用户名：user_ + 邮箱前缀 + _ + 随机3位数字 */
    private String generateUniqueUsername(String email) {
        String prefix = "user_" + email.split("@")[0].replaceAll("[^a-zA-Z0-9]", "");
        if (prefix.length() < 5) {
            prefix = "user_" + (System.currentTimeMillis() % 100000);
        }

        // 最多重试 10 次，避免碰撞
        for (int i = 0; i < 10; i++) {
            String username = prefix + "_" + (RANDOM.nextInt(900) + 100);
            QueryWrapper<User> qw = new QueryWrapper<>();
            qw.eq("username", username);
            if (userMapper.selectCount(qw) == 0) {
                return username;
            }
        }
        // 兜底：加时间戳
        return prefix + "_" + (System.currentTimeMillis() % 100000);
    }

    /** 组装 LoginVO */
    private LoginVO buildLoginVO(String token, User user) {
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUser(userVO);
        return vo;
    }
}
