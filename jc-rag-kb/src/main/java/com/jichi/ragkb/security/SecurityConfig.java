package com.jichi.ragkb.security;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.filter.SaServletFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

/**
 * Sa-Token 安全配置
 * 所有 /api/** 接口需要登录，/api/v1/auth/** 和 /actuator/** 放行
 */
@Configuration
public class SecurityConfig {
    @Bean
    public SaServletFilter saServletFilter() {
        return new SaServletFilter()
                .addExclude("/**")
                .addExclude("/actuator/**", "/api/v1/auth/**")
                .setAuth(obj -> SaRouter.match("/api/**", StpUtil::checkLogin))
                .setError(error -> {
                    SaHolder.getResponse().setStatus(HttpStatus.UNAUTHORIZED.value());
                    return "{\"code\":401,\"message\":\"请先登录\"}";
                });
    }
}
