package com.jichi.ragkb.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.jichi.ragkb.entity.ChatMessage;
import com.jichi.ragkb.mapper.ChatMessageMapper;
import com.jichi.ragkb.repository.ChatMessageRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 对话消息 Repository 实现
 */
@Repository
public class ChatMessageRepositoryImpl extends ServiceImpl<ChatMessageMapper, ChatMessage> implements ChatMessageRepository {
    @Override
    public boolean save(ChatMessage entity) {
        return super.save(entity);
    }

    @Override
    public boolean updateById(ChatMessage entity) {
        return super.updateById(entity);
    }

    @Override
    public ChatMessage findById(Long id) {
        return getById(id);
    }

    @Override
    public List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId) {
        return list(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getCreatedAt));
    }
}
