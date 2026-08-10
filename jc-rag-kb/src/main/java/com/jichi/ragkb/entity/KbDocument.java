package com.jichi.ragkb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 知识库文档实体类用于管理上传到知识库中的文档一个文档对应多个分块
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("kb_document")
public class KbDocument {
    /**
     * 文档主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 所属知识库ID
     */
    private Long kbId;
    /**
     * 文档文件名称
     */
    private String fileName;
    /**
     * 文档文件类型包括PDF DOCX MD和TXT
     */
    private String fileType;
    /**
     * 文档文件大小单位为字节
     */
    private Long fileSize;
    /**
     * 文档在MinIO中的对象路径
     */
    private String minioPath;
    /**
     * 文档处理状态
     */
    private DocumentStatus status = DocumentStatus.PENDING;
    /**
     * 文档处理失败原因
     */
    private String errorMsg;
    /**
     * 文档索引后的分块数量
     */
    private Integer chunkCount = 0;
    /**
     * 文档向量化消耗的Token数量
     */
    private Integer tokenCount = 0;
    /**
     * 文档版本号每次重建索引时递增旧版本分块通过版本号识别并删除
     */
    private Integer version = 1;
    /**
     * 上传人用户ID
     */
    private Long uploadedBy;
    /**
     * 文档上传时间
     */
    private LocalDateTime uploadedAt;
    /**
     * 文档最近一次索引完成时间
     */
    private LocalDateTime indexedAt;
    /**
     * 逻辑删除标识
     */
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