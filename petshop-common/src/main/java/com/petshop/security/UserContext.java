package com.petshop.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前登录用户上下文（基于 ThreadLocal）。
 * 业务代码用 UserContext.getUserId() / getRole() 取当前登录人。
 */
public class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static Long getUserId() {
        return HOLDER.get() == null ? null : HOLDER.get().getUserId();
    }

    public static String getRole() {
        return HOLDER.get() == null ? null : HOLDER.get().getRole();
    }

    public static void clear() {
        HOLDER.remove();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginUser {
        private Long userId;
        private String username;
        private String role;
    }
}
