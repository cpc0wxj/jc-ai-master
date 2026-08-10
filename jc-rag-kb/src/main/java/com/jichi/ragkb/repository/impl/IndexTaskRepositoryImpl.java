package com.jichi.ragkb.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.jichi.ragkb.entity.IndexTask;
import com.jichi.ragkb.mapper.IndexTaskMapper;
import com.jichi.ragkb.repository.IndexTaskRepository;
import org.springframework.stereotype.Repository;

/**
 * 索引任务 Repository 实现
 */
@Repository
public class IndexTaskRepositoryImpl extends ServiceImpl<IndexTaskMapper, IndexTask> implements IndexTaskRepository {
    @Override
    public boolean save(IndexTask entity) {
        return super.save(entity);
    }

    @Override
    public boolean updateById(IndexTask entity) {
        return super.updateById(entity);
    }

    @Override
    public IndexTask findById(Long id) {
        return getById(id);
    }

    @Override
    public IndexTask findTopByDocIdOrderByCreatedAtDesc(Long docId) {
        return getOne(new LambdaQueryWrapper<IndexTask>()
                .eq(IndexTask::getDocId, docId)
                .orderByDesc(IndexTask::getCreatedAt)
                .last("LIMIT 1"));
    }
}