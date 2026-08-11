package com.jichi.ragkb.repository;

import com.jichi.ragkb.entity.IndexTask;

/**
 * 索引任务 Repository 接口
 */
public interface IndexTaskRepository {
    /**
     * 新增任务（INSERT），主键自动回填到实体
     */
    boolean save(IndexTask entity);

    /**
     * 根据主键 ID 更新任务（UPDATE）
     */
    boolean updateById(IndexTask entity);

    /**
     * 根据主键 ID 查询任务
     */
    IndexTask findById(Long id);

    /**
     * 查询指定文档最近一次创建的索引任务
     */
    IndexTask findTopByDocIdOrderByCreatedAtDesc(Long docId);
}