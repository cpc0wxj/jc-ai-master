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
     * 向量相似度检索（余弦相似度）。
     * 使用 PGVector 的 <=> 操作符（余弦距离），ORDER BY 距离升序即按相似度降序。
     */
    List<DocChunk> findByVectorSimilarity(@Param("kbId") Long kbId, @Param("embedding") String embedding, @Param("topK") int topK);

    /**
     * 全文检索（PostgreSQL 全文搜索）。
     * 使用 to_tsquery('simple', :query) 匹配 content_tsv。
     */
    List<DocChunk> findByFullTextSearch(@Param("kbId") Long kbId, @Param("tsQuery") String tsQuery, @Param("topK") int topK);
}