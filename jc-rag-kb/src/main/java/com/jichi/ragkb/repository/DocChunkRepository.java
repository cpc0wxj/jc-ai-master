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
     * 删除指定文档的旧版本分块（重建索引时使用）
     */
    boolean deleteByDocIdAndDocVersionLessThan(Long docId, Integer version);

    /**
     * 查询分块总数
     */
    long count();

    /**
     * 按文档 ID 查询所有分块
     */
    List<DocChunk> findByDocId(Long docId);

    /**
     * 按知识库 ID 查询所有分块
     */
    List<DocChunk> findByKbId(Long kbId);

    /**
     * 按文档 ID 删除所有分块（删除文档时清理）
     */
    boolean deleteByDocId(Long docId);

    /**
     * 按 ID 列表批量查询
     */
    List<DocChunk> findByIds(List<Long> ids);

    /**
     * 多知识库向量相似度检索（单次 SQL 实现每个知识库 TopK 限制）
     * 使用 PostgreSQL 窗口函数 ROW_NUMBER() OVER (PARTITION BY kb_id)
     *
     * @param kbIds   知识库 ID 列表
     * @param embedding 向量字符串，格式 "[v1,v2,v3,...]"
     * @param topK    每个知识库的 TopK 数量
     * @param globalTopK 全局返回的最大数量（可选，可为 null 表示不限）
     */
    List<DocChunk> findByVectorSimilarityMultiKb(List<Long> kbIds, String embedding, int topK, Integer globalTopK);

    /**
     * 全文检索（PostgreSQL to_tsquery）
     */
    List<DocChunk> findByFullTextSearch(Long kbId, String tsQuery, int topK);
}