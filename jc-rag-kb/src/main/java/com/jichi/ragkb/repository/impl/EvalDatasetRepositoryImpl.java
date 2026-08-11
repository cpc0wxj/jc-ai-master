package com.jichi.ragkb.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.jichi.ragkb.entity.EvalDataset;
import com.jichi.ragkb.mapper.EvalDatasetMapper;
import com.jichi.ragkb.repository.EvalDatasetRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * RAG 评估数据集 Repository 实现
 */
@Repository
public class EvalDatasetRepositoryImpl extends ServiceImpl<EvalDatasetMapper, EvalDataset> implements EvalDatasetRepository {
    @Override
    public boolean save(EvalDataset entity) {
        return super.save(entity);
    }

    @Override
    public boolean updateById(EvalDataset entity) {
        return super.updateById(entity);
    }

    @Override
    public EvalDataset findById(Long id) {
        return getById(id);
    }

    @Override
    public List<EvalDataset> findByKbId(Long kbId) {
        return list(new LambdaQueryWrapper<EvalDataset>()
                .eq(EvalDataset::getKbId, kbId));
    }

    @Override
    public boolean deleteById(Long id) {
        return removeById(id);
    }
}
