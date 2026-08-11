package com.jichi.ragkb.repository;

import com.jichi.ragkb.entity.KbDocument;

import java.util.Collection;
import java.util.List;

/**
 * 知识库文档 Repository 接口
 */
public interface KbDocumentRepository {
    /**
     * 新增文档（INSERT），主键自动回填到实体
     */
    boolean save(KbDocument entity);

    /**
     * 根据主键 ID 更新文档（UPDATE）
     */
    boolean updateById(KbDocument entity);

    /**
     * 根据主键 ID 查询文档
     */
    KbDocument findById(Long id);

    /**
     * 查询文档总数
     */
    long count();

    /**
     * 按处理状态统计文档数量
     */
    long countByStatus(KbDocument.DocumentStatus status);

    /**
     * 查询指定知识库下未删除的文档列表
     */
    List<KbDocument> findByKbIdAndIsDeletedFalse(Long kbId);

    /**
     * 按 ID 集合批量查询文档
     */
    List<KbDocument> findAllById(Collection<Long> ids);
}