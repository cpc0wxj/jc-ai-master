package com.jichi.ragkb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jichi.ragkb.dto.EvalReport;
import com.jichi.ragkb.entity.EvalResult;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * RAG 评估结果 Mapper
 */
public interface EvalResultMapper extends BaseMapper<EvalResult> {
    /**
     * 按知识库ID和评估版本聚合统计
     *
     * @param kbId 知识库ID
     * @return 评估报告列表
     */
    List<EvalReport> aggregateByVersion(@Param("kbId") Long kbId);
}
