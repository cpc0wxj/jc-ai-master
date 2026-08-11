package com.jichi.ragkb.security;

/**
 * 存储当前请求的用户信息，通过 ThreadLocal 在请求链路中传递。
 * 由 Sa-Token 拦截器在请求进入时写入，无需每个方法手动传参。
 *
 * 使用 3 个独立 ThreadLocal + withInitial 默认值，
 * 即使在非 Web 请求线程（如测试、定时任务）中调用也不会 NPE。
 */
public class UserContext {
    private static final ThreadLocal<Long> USER_ID_THREAD_LOCAL = ThreadLocal.withInitial(() -> 1L);
    private static final ThreadLocal<String> DEPARTMENT_ID_THREAD_LOCAL = ThreadLocal.withInitial(() -> "default");
    private static final ThreadLocal<String> ROLE_THREAD_LOCAL = ThreadLocal.withInitial(() -> "admin");

    public static Long getUserId() {
        return USER_ID_THREAD_LOCAL.get();
    }

    public static String getDepartmentId() {
        return DEPARTMENT_ID_THREAD_LOCAL.get();
    }

    public static String getRole() {
        return ROLE_THREAD_LOCAL.get();
    }

    public static boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(ROLE_THREAD_LOCAL.get());
    }

    public static void set(Long userId, String departmentId, String role) {
        USER_ID_THREAD_LOCAL.set(userId);
        DEPARTMENT_ID_THREAD_LOCAL.set(departmentId);
        ROLE_THREAD_LOCAL.set(role);
    }

    public static void clear() {
        USER_ID_THREAD_LOCAL.remove();
        DEPARTMENT_ID_THREAD_LOCAL.remove();
        ROLE_THREAD_LOCAL.remove();
    }
}