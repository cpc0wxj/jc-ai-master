package com.jichi.ragkb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * RAG 评估结果实体类
 * 记录每次评估的检索命中情况、排名和生成质量分数
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("kb_eval_result")
public class EvalResult {
    /**
     * 评估结果主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 关联的数据集ID
     */
    private Long datasetId;
    /**
     * 评估版本（如：v1_chunk512_hybrid）
     */
    private String evalVersion;
    /**
     * 是否命中（期望 chunk 在召回结果中）
     */
    private Boolean hit;
    /**
     * 命中 chunk 的排名（用于 MRR 计算）
     */
    private Integer rank;
    /**
     * 实际生成的回答
     */
    private String actualAnswer;
    /**
     * RAGAS Faithfulness 分数
     */
    private Double faithfulness;
    /**
     * RAGAS Answer Relevancy 分数
     */
    private Double answerRelevancy;
    /**
     * 评估时间
     */
    private LocalDateTime evalAt;
}
