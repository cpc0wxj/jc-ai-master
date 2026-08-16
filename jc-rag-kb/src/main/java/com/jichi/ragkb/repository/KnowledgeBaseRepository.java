package com.jichi.ragkb.repository;

import com.jichi.ragkb.entity.KnowledgeBase;

import java.util.Collection;
import java.util.List;

/**
 * 知识库 Repository 接口
 */
public interface KnowledgeBaseRepository {
    /**
     * 新增知识库（INSERT）
     */
    boolean save(KnowledgeBase entity);

    /**
     * 根据主键 ID 更新知识库（UPDATE）
     */
    boolean updateById(KnowledgeBase entity);

    /**
     * 根据主键 ID 查询知识库
     */
    KnowledgeBase findById(Long id);

    /**
     * 查询所有未删除的知识库
     */
    List<KnowledgeBase> findByIsDeletedFalse();

    /**
     * 按部门ID查询未删除的知识库
     */
    List<KnowledgeBase> findByDepartmentIdAndIsDeletedFalse(String departmentId);

    /**
     * 查询所有公开且未删除的知识库
     */
    List<KnowledgeBase> findByIsPublicTrueAndIsDeletedFalse();

    /**
     * 按 ID 集合批量查询知识库
     */
    List<KnowledgeBase> findAllById(Collection<Long> ids);
}