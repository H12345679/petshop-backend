package com.petshop.user.service.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.petshop.common.BusinessException;
import com.petshop.security.JwtUtil;
import com.petshop.user.entity.User;
import com.petshop.user.entity.UserOauth;
import com.petshop.user.mapper.UserMapper;
import com.petshop.user.mapper.UserOauthMapper;
import com.petshop.user.model.dto.OAuthLoginDTO;
import com.petshop.user.model.vo.OAuthLoginVO;
import com.petshop.user.model.vo.UserVO;
import com.petshop.user.service.UserOauthService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class UserOauthServiceImpl implements UserOauthService {

    @Autowired
    private UserOauthMapper userOauthMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public OAuthLoginVO login(OAuthLoginDTO dto) {
        // 查是否已绑定
        QueryWrapper<UserOauth> qw = new QueryWrapper<>();
        qw.eq("provider", dto.getProvider())
          .eq("open_id", dto.getOpenId());
        UserOauth oauth = userOauthMapper.selectOne(qw);

        if (oauth != null) {
            // 已绑定：查找用户并登录
            User user = userMapper.selectById(oauth.getUserId());
            if (user == null) {
                throw new BusinessException("绑定的用户不存在");
            }
            if (user.getStatus() != null && user.getStatus() == 0) {
                throw new BusinessException("账号已被禁用");
            }

            // 更新最后登录时间
            user.setLastLoginTime(LocalDateTime.now(ZoneId.systemDefault()));
            userMapper.updateById(user);

            // 签发 JWT
            String token = jwtUtil.createToken(user.getId(), user.getUsername(), user.getRole());

            UserVO userVO = new UserVO();
            BeanUtils.copyProperties(user, userVO);

            OAuthLoginVO vo = new OAuthLoginVO();
            vo.setBindRequired(false);
            vo.setToken(token);
            vo.setUser(userVO);
            return vo;
        }

        // 未绑定
        OAuthLoginVO vo = new OAuthLoginVO();
        vo.setBindRequired(true);
        vo.setProvider(dto.getProvider());
        vo.setOpenId(dto.getOpenId());
        return vo;
    }
}
