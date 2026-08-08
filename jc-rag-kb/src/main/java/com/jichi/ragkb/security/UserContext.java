package com.jichi.ragkb.security;

import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

/**
 * 存储当前请求的用户信息，通过 ThreadLocal 在请求链路中传递。
 * 由 Sa-Token 拦截器在请求进入时写入，无需每个方法手动传参。
 */
public class UserContext {
    private static final ThreadLocal<UserInfo> CONTEXT = new ThreadLocal<>();

    public static void set(UserInfo userInfo) {
        CONTEXT.set(userInfo);
    }

    public static UserInfo get() {
        UserInfo userInfo = CONTEXT.get();

        if (Objects.isNull(userInfo)) {
            throw new IllegalStateException("UserContext 未初始化，请检查认证拦截器配置");
        }

        return userInfo;
    }

    public static Long getUserId() {
        return get().getUserId();
    }

    public static String getDepartmentId() {
        return get().getDepartmentId();
    }

    public static boolean isAdmin() {
        return "ADMIN".equals(get().getRole());
    }

    public static void clear() {
        CONTEXT.remove();
    }

    @Getter
    @Setter
    public static class UserInfo {
        private Long userId;
        private String username;
        private String departmentId;
        private String role;   // ADMIN / MEMBER
    }
}