package com.jichi.ragkb.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 上下文参数配置
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rag.context")
public class RagContextProperties {
    /**
     * 传给模型的最大 context Token 数
     */
    private Integer maxTokens;
}
