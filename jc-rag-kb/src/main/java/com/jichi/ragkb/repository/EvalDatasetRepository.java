package com.jichi.ragkb.repository;

import com.jichi.ragkb.entity.EvalDataset;

import java.util.List;

/**
 * RAG 评估数据集 Repository 接口
 */
public interface EvalDatasetRepository {
    /**
     * 新增数据集（INSERT）
     */
    boolean save(EvalDataset entity);

    /**
     * 根据主键 ID 更新数据集（UPDATE）
     */
    boolean updateById(EvalDataset entity);

    /**
     * 根据主键 ID 查询数据集
     */
    EvalDataset findById(Long id);

    /**
     * 按知识库ID查询数据集列表
     */
    List<EvalDataset> findByKbId(Long kbId);

    /**
     * 根据主键 ID 删除数据集
     */
    boolean deleteById(Long id);
}
