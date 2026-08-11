package com.jichi.ragkb.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/**
 * 请求追踪过滤器
 * 为每个请求生成 traceId，注入 MDC 供日志使用
 * application.yml 中日志格式已配置 [%X{traceId}]，自动打印
 */
@Component
public class TraceFilter implements Filter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) request;

        // 优先使用客户端传入的 traceId（方便分布式追踪），否则生成 8 位短 UUID
        String traceId = httpReq.getHeader(TRACE_ID_HEADER);
        if (Objects.isNull(traceId) || StringUtils.isBlank(traceId)) {
            traceId = UUID.randomUUID().toString().substring(0, 8);
        }

        MDC.put("traceId", traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("traceId");
        }
    }
}
