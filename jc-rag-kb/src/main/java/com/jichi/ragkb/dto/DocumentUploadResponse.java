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

    public static DocumentUploadResponse submitted(Long docId, String fileName) {
        return new DocumentUploadResponse()
                .setDocId(docId)
                .setFileName(fileName)
                .setStatus("PENDING")
                .setMessage("文档已上传，正在后台索引，请通过 /status 接口查询进度");
    }
}
