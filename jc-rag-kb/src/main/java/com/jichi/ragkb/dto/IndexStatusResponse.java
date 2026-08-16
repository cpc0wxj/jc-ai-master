package com.jichi.ragkb.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 索引状态查询响应
 */
@Getter
@Setter
@Accessors(chain = true)
public class IndexStatusResponse {
    private Long docId;
    private String fileName;
    /**
     * 文档状态：PENDING / PROCESSING / DONE / FAILED
     */
    private String status;
    private String errorMsg;
    private Integer chunkCount;
    private Integer tokenCount;
    private String indexedAt;
    private Integer retryCount;
}