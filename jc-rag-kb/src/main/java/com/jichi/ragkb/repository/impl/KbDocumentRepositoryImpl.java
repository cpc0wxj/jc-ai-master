package com.jichi.ragkb.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.jichi.ragkb.entity.KbDocument;
import com.jichi.ragkb.mapper.KbDocumentMapper;
import com.jichi.ragkb.repository.KbDocumentRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 知识库文档 Repository 实现
 */
@Repository
public class KbDocumentRepositoryImpl extends ServiceImpl<KbDocumentMapper, KbDocument> implements KbDocumentRepository {
    @Override
    public KbDocument findById(Long id) {
        return getById(id);
    }

    @Override
    public boolean save(KbDocument entity) {
        return super.save(entity);
    }

    @Override
    public boolean updateById(KbDocument entity) {
        return super.updateById(entity);
    }

    @Override
    public long count() {
        return super.count();
    }

    @Override
    public long countByStatus(KbDocument.DocumentStatus status) {
        return count(new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getStatus, status));
    }

    @Override
    public List<KbDocument> findByKbIdAndIsDeletedFalse(Long kbId) {
        return list(new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getKbId, kbId)
                .eq(KbDocument::getIsDeleted, false));
    }
}