package com.jichi.ragkb.repository;

import com.jichi.ragkb.entity.ChatMessage;

import java.util.List;

/**
 * 对话消息 Repository 接口
 */
public interface ChatMessageRepository {
    /**
     * 新增消息（INSERT）
     */
    boolean save(ChatMessage entity);

    /**
     * 根据主键 ID 更新消息（UPDATE）
     */
    boolean updateById(ChatMessage entity);

    /**
     * 根据主键 ID 查询消息
     */
    ChatMessage findById(Long id);

    /**
     * 按会话ID查询消息列表（按创建时间升序）
     */
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId);
}
