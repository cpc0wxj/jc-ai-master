package com.jichi.ragkb.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rag.cache")
public class RagCacheProperties {
    /**
     * Embedding 缓存有效期
     */
    private Duration embeddingTtl;
    /**
     * 查询结果缓存有效期
     */
    private Duration queryTtl;
}