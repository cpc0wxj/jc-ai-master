package com.jichi.ragkb.repository;

import com.jichi.ragkb.entity.DocChunk;

import java.util.List;

/**
 * 文档分块 Repository 接口
 */
public interface DocChunkRepository {
    /**
     * 批量新增分块（INSERT）
     */
    boolean saveBatch(List<DocChunk> entities);

    /**
     * 查询分块总数
     */
    long count();

    /**
     * 删除指定文档的旧版本分块（重建索引时使用）
     */
    boolean deleteByDocIdAndDocVersionLessThan(Long docId, Integer version);

    /**
     * 按文档 ID 查询所有分块
     */
    List<DocChunk> findByDocId(Long docId);

    /**
     * 按 ID 列表批量查询
     */
    List<DocChunk> findByIds(List<Long> ids);

    /**
     * 向量相似度检索（PGVector 余弦距离）
     */
    List<DocChunk> findByVectorSimilarity(Long kbId, String embedding, int topK);

    /**
     * 全文检索（PostgreSQL to_tsquery）
     */
    List<DocChunk> findByFullTextSearch(Long kbId, String tsQuery, int topK);
}