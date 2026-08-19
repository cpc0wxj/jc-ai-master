package com.jichi.ragkb.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.jichi.ragkb.entity.ChatSession;
import com.jichi.ragkb.mapper.ChatSessionMapper;
import com.jichi.ragkb.repository.ChatSessionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 对话会话 Repository 实现
 */
@Repository
public class ChatSessionRepositoryImpl extends ServiceImpl<ChatSessionMapper, ChatSession> implements ChatSessionRepository {
    @Override
    public boolean save(ChatSession entity) {
        return super.save(entity);
    }

    @Override
    public boolean updateById(ChatSession entity) {
        return super.updateById(entity);
    }

    @Override
    public ChatSession findById(String id) {
        return getById(id);
    }

    @Override
    public List<ChatSession> findByUserIdAndIsDeletedFalseOrderByLastActiveAtDesc(Long userId) {
        LambdaQueryWrapper<ChatSession> lambdaQueryWrapper = Wrappers.<ChatSession>lambdaQuery()
                .eq(ChatSession::getUserId, userId)
                .eq(ChatSession::getIsDeleted, false)
                .orderByDesc(ChatSession::getLastActiveAt);
        return list(lambdaQueryWrapper);
    }
}