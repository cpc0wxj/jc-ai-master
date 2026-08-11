package com.jichi.ragkb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jichi.ragkb.config.LongArrayTypeHandler;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * RAG 评估数据集实体类
 * 存储人工标注的评估问题和期望答案，用于自动化评估检索和生成质量
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName(value = "kb_eval_dataset", autoResultMap = true)
public class EvalDataset {
    /**
     * 数据集主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 所属知识库ID
     */
    private Long kbId;
    /**
     * 评估问题
     */
    private String question;
    /**
     * 期望答案
     */
    private String expectedAnswer;
    /**
     * 期望召回的 chunk ID 列表（PostgreSQL BIGINT[] 数组）
     */
    @TableField(value = "expected_chunk_ids", typeHandler = LongArrayTypeHandler.class)
    private Long[] expectedChunkIds;
    /**
     * 创建者用户ID
     */
    private Long createdBy;
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
