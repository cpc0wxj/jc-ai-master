package com.jichi.ragkb.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 检索参数配置
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rag.retrieval")
public class RagRetrievalProperties {
    /**
     * 向量检索召回数量
     */
    private Integer vectorTopK;
    /**
     * 全文检索召回数量
     */
    private Integer fulltextTopK;
    /**
     * RRF 融合后最终返回的 chunk 数量
     */
    private Integer returnTopN;
    /**
     * 最低相似度阈值（低于此分数的 chunk 过滤掉）
     */
    private Double minScore;
}
