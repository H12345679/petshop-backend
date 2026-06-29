package com.petshop.user.service;

import com.petshop.common.PageResult;
import com.petshop.user.model.dto.ChangePasswordDTO;
import com.petshop.user.model.dto.LoginDTO;
import com.petshop.user.model.dto.RegisterDTO;
import com.petshop.user.model.dto.UpdateUserDTO;
import com.petshop.user.model.dto.UserRoleDTO;
import com.petshop.user.model.dto.UserStatusDTO;
import com.petshop.user.model.vo.LoginVO;
import com.petshop.user.model.vo.UserManageVO;
import com.petshop.user.model.vo.UserVO;

public interface UserService {

    /** 用户注册 */
    UserVO register(RegisterDTO dto);

    /** 用户登录 */
    LoginVO login(LoginDTO dto);

    /** 获取当前登录用户信息 */
    UserVO getCurrentUser(Long userId);

    /** 修改当前用户信息 */
    void updateCurrentUser(Long userId, UpdateUserDTO dto);

    /** 修改当前用户密码 */
    void changePassword(Long userId, ChangePasswordDTO dto);

    /** 后台用户管理列表（ADMIN） */
    PageResult<UserManageVO> manageList(int current, int size, String username);

    /** 后台启用/禁用用户（ADMIN） */
    void updateUserStatus(Long userId, UserStatusDTO dto);

    /** 后台授予/变更用户角色（ADMIN） */
    void updateUserRole(Long userId, UserRoleDTO dto);
}
