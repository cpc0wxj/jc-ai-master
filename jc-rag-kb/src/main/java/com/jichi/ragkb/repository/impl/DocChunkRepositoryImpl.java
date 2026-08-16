package com.jichi.ragkb.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
    public boolean deleteByDocIdAndDocVersionLessThan(Long docId, Integer version) {
        LambdaQueryWrapper<DocChunk> lambdaQueryWrapper = Wrappers.<DocChunk>lambdaQuery()
                .eq(DocChunk::getDocId, docId)
                .lt(DocChunk::getDocVersion, version);
        return remove(lambdaQueryWrapper);
    }

    @Override
    public long count() {
        return super.count();
    }

    @Override
    public List<DocChunk> findByDocId(Long docId) {
        LambdaQueryWrapper<DocChunk> lambdaQueryWrapper = Wrappers.<DocChunk>lambdaQuery()
                .eq(DocChunk::getDocId, docId);
        return list(lambdaQueryWrapper);
    }

    @Override
    public List<DocChunk> findByKbId(Long kbId) {
        LambdaQueryWrapper<DocChunk> lambdaQueryWrapper = Wrappers.<DocChunk>lambdaQuery()
                .eq(DocChunk::getKbId, kbId);
        return list(lambdaQueryWrapper);
    }

    @Override
    public boolean deleteByDocId(Long docId) {
        LambdaQueryWrapper<DocChunk> lambdaQueryWrapper = Wrappers.<DocChunk>lambdaQuery()
                .eq(DocChunk::getDocId, docId);
        return remove(lambdaQueryWrapper);
    }

    @Override
    public List<DocChunk> findByIds(List<Long> ids) {
        LambdaQueryWrapper<DocChunk> lambdaQueryWrapper = Wrappers.<DocChunk>lambdaQuery()
                .in(DocChunk::getId, ids);
        return list(lambdaQueryWrapper);
    }

    @Override
    public List<DocChunk> findByVectorSimilarityMultiKb(List<Long> kbIds, String embedding, int topK, Integer globalTopK) {
        return baseMapper.findByVectorSimilarityMultiKb(kbIds, embedding, topK, globalTopK);
    }

    @Override
    public List<DocChunk> findByFullTextSearch(Long kbId, String tsQuery, int topK) {
        return baseMapper.findByFullTextSearch(kbId, tsQuery, topK);
    }
}