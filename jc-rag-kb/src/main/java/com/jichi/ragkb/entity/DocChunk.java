package com.jichi.ragkb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jichi.ragkb.config.FloatVectorTypeHandler;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 文档分块实体类每条记录是一个可检索的最小单元
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName(value = "kb_doc_chunk", autoResultMap = true)
public class DocChunk {
    /**
     * 文档分块主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 所属文档ID
     */
    private Long docId;
    /**
     * 所属知识库ID冗余存储以避免检索时关联文档表
     */
    private Long kbId;
    /**
     * 分块在文档中的顺序从零开始
     */
    private Integer chunkIndex;
    /**
     * 文档分块原文
     */
    private String content;
    /**
     * 文档分块的向量表示使用PGVector的vector类型维度为1024
     */
    @TableField(value = "embedding", typeHandler = FloatVectorTypeHandler.class)
    private float[] embedding;
    /**
     * 文档分块所在页码主要用于PDF文档
     */
    private Integer pageNum;
    /**
     * 文档分块所在的章节标题
     */
    private String sectionTitle;
    /**
     * 文档分块的Token估算数量
     */
    private Integer tokenCount = 0;
    /**
     * 对应的文档版本号重建索引后删除旧版本时使用
     */
    private Integer docVersion;
    /**
     * 文档分块创建时间
     */
    private LocalDateTime createdAt;
}