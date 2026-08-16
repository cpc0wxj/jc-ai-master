package com.jichi.ragkb.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.jichi.ragkb.entity.KnowledgeBase;
import com.jichi.ragkb.mapper.KnowledgeBaseMapper;
import com.jichi.ragkb.repository.KnowledgeBaseRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 知识库 Repository 实现
 */
@Repository
public class KnowledgeBaseRepositoryImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBase> implements KnowledgeBaseRepository {
    @Override
    public boolean save(KnowledgeBase entity) {
        return super.save(entity);
    }

    @Override
    public boolean updateById(KnowledgeBase entity) {
        return super.updateById(entity);
    }

    @Override
    public KnowledgeBase findById(Long id) {
        return getById(id);
    }

    @Override
    public List<KnowledgeBase> findByIsDeletedFalse() {
        LambdaQueryWrapper<KnowledgeBase> lambdaQueryWrapper = Wrappers.<KnowledgeBase>lambdaQuery()
                .eq(KnowledgeBase::getIsDeleted, false);
        return list(lambdaQueryWrapper);
    }

    @Override
    public List<KnowledgeBase> findByDepartmentIdAndIsDeletedFalse(String departmentId) {
        LambdaQueryWrapper<KnowledgeBase> lambdaQueryWrapper = Wrappers.<KnowledgeBase>lambdaQuery()
                .eq(KnowledgeBase::getDepartmentId, departmentId)
                .eq(KnowledgeBase::getIsDeleted, false);
        return list(lambdaQueryWrapper);
    }

    @Override
    public List<KnowledgeBase> findByIsPublicTrueAndIsDeletedFalse() {
        LambdaQueryWrapper<KnowledgeBase> lambdaQueryWrapper = Wrappers.<KnowledgeBase>lambdaQuery()
                .eq(KnowledgeBase::getIsPublic, true)
                .eq(KnowledgeBase::getIsDeleted, false);
        return list(lambdaQueryWrapper);
    }

    @Override
    public List<KnowledgeBase> findAllById(Collection<Long> ids) {
        LambdaQueryWrapper<KnowledgeBase> lambdaQueryWrapper = Wrappers.<KnowledgeBase>lambdaQuery()
                .in(KnowledgeBase::getId, ids);
        return list(lambdaQueryWrapper);
    }
}