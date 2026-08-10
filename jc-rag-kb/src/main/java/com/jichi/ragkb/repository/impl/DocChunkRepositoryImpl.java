package com.jichi.ragkb.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.jichi.ragkb.entity.DocChunk;
import com.jichi.ragkb.mapper.DocChunkMapper;
import com.jichi.ragkb.repository.DocChunkRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文档分块 Repository 实现
 */
@Repository
public class DocChunkRepositoryImpl extends ServiceImpl<DocChunkMapper, DocChunk> implements DocChunkRepository {
    @Override
    public boolean saveBatch(List<DocChunk> entities) {
        return super.saveBatch(entities);
    }

    @Override
    public long count() {
        return super.count();
    }

    @Override
    public boolean deleteByDocIdAndDocVersionLessThan(Long docId, Integer version) {
        return remove(new LambdaQueryWrapper<DocChunk>()
                .eq(DocChunk::getDocId, docId)
                .lt(DocChunk::getDocVersion, version));
    }

    @Override
    public List<DocChunk> findByDocId(Long docId) {
        return list(new LambdaQueryWrapper<DocChunk>()
                .eq(DocChunk::getDocId, docId));
    }

    @Override
    public List<DocChunk> findByIds(List<Long> ids) {
        return list(new LambdaQueryWrapper<DocChunk>()
                .in(DocChunk::getId, ids));
    }

    @Override
    public List<DocChunk> findByVectorSimilarity(Long kbId, String embedding, int topK) {
        return baseMapper.findByVectorSimilarity(kbId, embedding, topK);
    }

    @Override
    public List<DocChunk> findByFullTextSearch(Long kbId, String tsQuery, int topK) {
        return baseMapper.findByFullTextSearch(kbId, tsQuery, topK);
    }
}