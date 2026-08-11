package com.jichi.ragkb.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.jichi.ragkb.dto.ApiResponse;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

/**
 * 认证接口
 * 演示用的登录接口，生产中应对接企业 SSO / LDAP
 */
@RestController
@RequestMapping("/api/v1/auth")
@Slf4j
public class AuthController {

    // 演示用用户数据（生产中从数据库查）
    private static final Map<String, UserProfile> DEMO_USERS = Map.of(
            "hr001", new UserProfile(1L, "HR", "MEMBER"),
            "tech001", new UserProfile(2L, "TECH", "MEMBER"),
            "admin", new UserProfile(3L, "ALL", "ADMIN")
    );

    @PostMapping("/login")
    public ApiResponse<String> login(@RequestBody LoginRequest loginRequest) {
        UserProfile userProfile = DEMO_USERS.get(loginRequest.getUsername());
        if (Objects.isNull(userProfile) || !"demo123".equals(loginRequest.getPassword())) {
            return ApiResponse.error(401, "用户名或密码错误");
        }

        StpUtil.login(userProfile.userId());
        // 把用户附加信息存入 Session，Interceptor 通过 StpUtil.getSession().get() 读取
        StpUtil.getSession().set("departmentId", userProfile.departmentId());
        StpUtil.getSession().set("role", userProfile.role());

        String token = StpUtil.getTokenValue();
        log.info("AuthController.login username={},userId={},dept={}", loginRequest.getUsername(), userProfile.userId(), userProfile.departmentId());

        return ApiResponse.ok(token);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        StpUtil.logout();
        return ApiResponse.ok(null);
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    static class LoginRequest {
        private String username;
        private String password;
    }

    record UserProfile(Long userId, String departmentId, String role) {
    }
}