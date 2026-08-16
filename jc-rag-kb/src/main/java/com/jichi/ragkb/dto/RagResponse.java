package com.jichi.ragkb.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * RAG 查询响应
 */
@Getter
@Setter
@Accessors(chain = true)
public class RagResponse {
    private String answer;
    private List<Source> sources;
    private int latencyMs;
    private boolean notFound;

    /**
     * 引用来源
     */
    @Getter
    @Setter
    @Accessors(chain = true)
    public static class Source {
        private Long chunkId;
        private Long docId;
        private Integer pageNum;
        private String sectionTitle;
        /**
         * 相关段落摘要（前200字）
         */
        private String excerpt;
        /**
         * 相关性分数
         */
        private double score;
        /**
         * 文档名称
         */
        private String docName;
    }

    public static RagResponse notFound() {
        return new RagResponse()
                .setAnswer("""
                        在知识库中未找到与该问题相关的内容。建议您：
                        1. 确认问题是否属于该知识库的覆盖范围
                        2. 尝试更换关键词提问
                        3. 联系相关部门获取准确信息""")
                .setSources(List.of())
                .setNotFound(true);
    }
}