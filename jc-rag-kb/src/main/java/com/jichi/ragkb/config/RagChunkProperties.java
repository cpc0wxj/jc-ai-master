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
    private Integer size;
    /**
     * 相邻块的重叠字符数，避免信息在块边界被截断
     */
    private Integer overlap;
    /**
     * 是否启用结构感知分块（按段落/标题断点）
     */
    private Boolean structureAware;
}