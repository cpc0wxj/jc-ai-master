package com.jichi.ragkb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jichi.ragkb.entity.DocChunk;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文档分块 Mapper（MyBatis-Plus）
 */
public interface DocChunkMapper extends BaseMapper<DocChunk> {
    /**
     * 多知识库向量相似度检索（单次 SQL 实现每个知识库 TopK 限制）。
     * 使用 PostgreSQL 窗口函数 ROW_NUMBER() OVER (PARTITION BY kb_id)
     *
     * @param kbIds      知识库 ID 列表
     * @param embedding  向量字符串，格式 "[v1,v2,v3,...]"
     * @param topK       每个知识库的 TopK 数量
     * @param globalTopK 全局返回的最大数量（可为 null 表示不限）
     */
    List<DocChunk> findByVectorSimilarityMultiKb(@Param("kbIds") List<Long> kbIds, @Param("embedding") String embedding, @Param("topK") int topK, @Param("globalTopK") Integer globalTopK);

    /**
     * 全文检索（PostgreSQL 全文搜索）。
     * 使用 to_tsquery('simple', :query) 匹配 content_tsv。
     */
    List<DocChunk> findByFullTextSearch(@Param("kbId") Long kbId, @Param("tsQuery") String tsQuery, @Param("topK") int topK);
}