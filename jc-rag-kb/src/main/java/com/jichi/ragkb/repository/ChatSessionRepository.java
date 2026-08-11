package com.jichi.ragkb.repository;

import com.jichi.ragkb.entity.ChatSession;

import java.util.List;

/**
 * 对话会话 Repository 接口
 */
public interface ChatSessionRepository {
    /**
     * 新增会话（INSERT）
     */
    boolean save(ChatSession entity);

    /**
     * 根据主键 ID 更新会话（UPDATE）
     */
    boolean updateById(ChatSession entity);

    /**
     * 根据主键 ID 查询会话
     */
    ChatSession findById(String id);

    /**
     * 按用户ID查询未删除的会话列表（按最近活跃时间降序）
     */
    List<ChatSession> findByUserIdAndIsDeletedFalseOrderByLastActiveAtDesc(Long userId);
}
