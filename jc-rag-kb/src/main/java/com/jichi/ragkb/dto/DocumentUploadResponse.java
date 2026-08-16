package com.jichi.ragkb.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 文档上传响应
 */
@Getter
@Setter
@Accessors(chain = true)
public class DocumentUploadResponse {
    private Long docId;

    private String fileName;
    /**
     * 文档状态（PENDING 表示已提交，等待索引）
     */
    private String status;

    private String message;
}