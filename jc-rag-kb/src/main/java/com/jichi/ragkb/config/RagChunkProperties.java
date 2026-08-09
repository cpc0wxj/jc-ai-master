package com.jichi.ragkb.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rag.chunk")
public class RagChunkProperties {
    /**
     * 每块最大字符数
     */
    private int size;
    /**
     * 相邻块的重叠字符数，避免信息在块边界被截断
     */
    private int overlap;
}