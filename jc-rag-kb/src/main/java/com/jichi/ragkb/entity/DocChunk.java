package com.jichi.ragkb.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 文档分块实体类每条记录是一个可检索的最小单元
 */
@Getter
@Setter
@Entity
@Accessors(chain = true)
@Table(name = "kb_doc_chunk")
public class DocChunk {
    /**
     * 文档分块主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /**
     * 所属文档ID
     */
    @Column(name = "doc_id", nullable = false)
    private Long docId;
    /**
     * 所属知识库ID冗余存储以避免检索时关联文档表
     */
    @Column(name = "kb_id", nullable = false)
    private Long kbId;
    /**
     * 分块在文档中的顺序从零开始
     */
    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;
    /**
     * 文档分块原文
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    /**
     * 文档分块的向量表示使用PGVector的vector类型维度为1024
     */
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 1024)
    @Column(name = "embedding", columnDefinition = "vector(1024)")
    private float[] embedding;
    /**
     * 文档分块所在页码主要用于PDF文档
     */
    @Column(name = "page_num")
    private Integer pageNum;
    /**
     * 文档分块所在的章节标题
     */
    @Column(name = "section_title", length = 500)
    private String sectionTitle;
    /**
     * 文档分块的Token估算数量
     */
    @Column(name = "token_count", nullable = false)
    private Integer tokenCount = 0;
    /**
     * 对应的文档版本号重建索引后删除旧版本时使用
     */
    @Column(name = "doc_version", nullable = false)
    private Integer docVersion;
    /**
     * 文档分块创建时间
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}