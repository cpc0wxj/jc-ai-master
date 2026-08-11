package com.jichi.ragkb.repository.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.jichi.ragkb.dto.EvalReport;
import com.jichi.ragkb.entity.EvalResult;
import com.jichi.ragkb.mapper.EvalResultMapper;
import com.jichi.ragkb.repository.EvalResultRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * RAG 评估结果 Repository 实现
 */
@Repository
public class EvalResultRepositoryImpl extends ServiceImpl<EvalResultMapper, EvalResult> implements EvalResultRepository {
    @Override
    public boolean save(EvalResult entity) {
        return super.save(entity);
    }

    @Override
    public boolean saveBatch(List<EvalResult> entities) {
        return super.saveBatch(entities);
    }

    @Override
    public List<EvalReport> aggregateByVersion(Long kbId) {
        return baseMapper.aggregateByVersion(kbId);
    }
}
