package com.jichi.ragkb.config;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ChunkConfig {
    /**
     * 每块最大字符数
     */
    private int chunkSize;
    /**
     * 相邻块的重叠字符数，避免信息在块边界被截断
     */
    private int chunkOverlap;
    /**
     * 是否启用结构感知分块（按段落/标题断点）
     */
    private Boolean structureAware;
}