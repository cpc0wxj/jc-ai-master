package com.jichi.ragkb.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 知识库文档实体类用于管理上传到知识库中的文档一个文档对应多个分块
 */
@Getter
@Setter
@Entity
@Accessors(chain = true)
@Table(name = "kb_document")
public class KbDocument {
    /**
     * 文档主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /**
     * 所属知识库ID
     */
    @Column(name = "kb_id", nullable = false)
    private Long kbId;
    /**
     * 文档文件名称
     */
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;
    /**
     * 文档文件类型包括PDF DOCX MD和TXT
     */
    @Column(name = "file_type", nullable = false, length = 20)
    private String fileType;
    /**
     * 文档文件大小单位为字节
     */
    @Column(name = "file_size", nullable = false)
    private Long fileSize;
    /**
     * 文档在MinIO中的对象路径
     */
    @Column(name = "minio_path", nullable = false, length = 500)
    private String minioPath;
    /**
     * 文档处理状态
     */
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private DocumentStatus status = DocumentStatus.PENDING;
    /**
     * 文档处理失败原因
     */
    @Column(name = "error_msg", columnDefinition = "TEXT")
    private String errorMsg;
    /**
     * 文档索引后的分块数量
     */
    @Column(name = "chunk_count")
    private Integer chunkCount = 0;
    /**
     * 文档向量化消耗的Token数量
     */
    @Column(name = "token_count")
    private Integer tokenCount = 0;
    /**
     * 文档版本号每次重建索引时递增旧版本分块通过版本号识别并删除
     */
    @Column(nullable = false)
    private Integer version = 1;
    /**
     * 上传人用户ID
     */
    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;
    /**
     * 文档上传时间
     */
    @CreationTimestamp
    @Column(name = "uploaded_at", updatable = false)
    private LocalDateTime uploadedAt;
    /**
     * 文档最近一次索引完成时间
     */
    @Column(name = "indexed_at")
    private LocalDateTime indexedAt;
    /**
     * 逻辑删除标识
     */
    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    /**
     * 文档处理状态枚举
     */
    public enum DocumentStatus {
        /**
         * 待处理
         */
        PENDING,
        /**
         * 处理中
         */
        PROCESSING,
        /**
         * 处理完成
         */
        DONE,
        /**
         * 处理失败
         */
        FAILED
    }
}