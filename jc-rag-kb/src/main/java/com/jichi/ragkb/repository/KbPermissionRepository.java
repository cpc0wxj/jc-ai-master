package com.jichi.ragkb.repository;

import com.jichi.ragkb.entity.KbPermission;

import java.util.List;

/**
 * 知识库权限 Repository 接口
 */
public interface KbPermissionRepository {
    /**
     * 新增权限（INSERT）
     */
    boolean save(KbPermission entity);

    /**
     * 按主体类型和主体ID查询权限列表
     */
    List<KbPermission> findBySubjectTypeAndSubjectId(String subjectType, String subjectId);

    /**
     * 判断指定主体对指定知识库是否有权限
     */
    boolean existsByKbIdAndSubjectTypeAndSubjectId(Long kbId, String subjectType, String subjectId);

    /**
     * 判断指定主体对指定知识库是否有指定权限级别
     */
    boolean existsByKbIdAndSubjectTypeAndSubjectIdAndPermissionIn(
            Long kbId, String subjectType, String subjectId, List<String> permissions);

    /**
     * 按知识库ID查询权限列表
     */
    List<KbPermission> findByKbId(Long kbId);

    /**
     * 按知识库ID删除所有权限
     */
    boolean deleteByKbId(Long kbId);
}
