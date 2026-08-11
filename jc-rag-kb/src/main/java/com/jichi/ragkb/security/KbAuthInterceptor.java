package com.jichi.ragkb.security;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Objects;

/**
 * 知识库权限拦截器
 * 在请求进入 Controller 前，从 Sa-Token Session 读取用户信息写入 UserContext
 * 具体的知识库权限校验在 PermissionService 中执行（Controller 调用），此拦截器只负责初始化 UserContext
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KbAuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                              HttpServletResponse response,
                              Object handler) {
        if (!StpUtil.isLogin()) {
            // 未登录的请求让 Sa-Token Filter 处理
            return true;
        }

        // 从 Sa-Token Session 获取用户信息，写入 ThreadLocal
        String userId = String.valueOf(StpUtil.getLoginId());
        Object deptObj = StpUtil.getSession().get("departmentId");
        Object roleObj = StpUtil.getSession().get("role");

        String deptId = Objects.nonNull(deptObj) ? deptObj.toString() : "";
        String role = Objects.nonNull(roleObj) ? roleObj.toString() : "MEMBER";

        UserContext.set(Long.parseLong(userId), deptId, role);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                 HttpServletResponse response,
                                 Object handler, Exception ex) {
        // 请求结束后清理，防止内存泄漏
        UserContext.clear();
    }
}
