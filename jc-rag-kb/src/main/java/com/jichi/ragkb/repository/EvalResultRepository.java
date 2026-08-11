package com.jichi.ragkb.repository;

import com.jichi.ragkb.dto.EvalReport;
import com.jichi.ragkb.entity.EvalResult;

import java.util.List;

/**
 * RAG 评估结果 Repository 接口
 */
public interface EvalResultRepository {
    /**
     * 新增评估结果（INSERT）
     */
    boolean save(EvalResult entity);

    /**
     * 批量新增评估结果（INSERT）
     */
    boolean saveBatch(List<EvalResult> entities);

    /**
     * 按知识库ID聚合统计评估报告（按评估版本分组）
     */
    List<EvalReport> aggregateByVersion(Long kbId);
}
