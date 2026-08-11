package com.jichi.ragkb.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Reranker 精排服务配置
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "reranker")
public class RerankerProperties {
    /**
     * Reranker API 地址
     */
    private String endpoint;
    /**
     * API 密钥
     */
    private String apiKey;
    /**
     * 模型名称
     */
    private String model;
    /**
     * 超时时间（毫秒），超过则降级
     */
    private Long timeoutMs;
    /**
     * 精排后保留的 Top N 数量
     */
    private Integer topN;
}
