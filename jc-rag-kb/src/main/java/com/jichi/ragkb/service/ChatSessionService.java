package com.jichi.ragkb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jichi.ragkb.entity.ChatMessage;
import com.jichi.ragkb.entity.ChatSession;
import com.jichi.ragkb.repository.ChatMessageRepository;
import com.jichi.ragkb.repository.ChatSessionRepository;
import com.jichi.ragkb.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 对话会话服务
 * 管理多轮对话的会话创建、消息保存和历史查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionService {
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    /** 最多保留的历史轮数（超出后截断旧消息） */
    private static final int MAX_HISTORY_ROUNDS = 5;

    /**
     * 获取已有会话，或创建新会话
     */
    @Transactional
    public String getOrCreateSession(String sessionId, List<Long> kbIds) {
        if (StringUtils.isNotBlank(sessionId)) {
            ChatSession existing = sessionRepository.findById(sessionId);
            if (Objects.nonNull(existing)) {
                existing.setLastActiveAt(LocalDateTime.now());
                sessionRepository.updateById(existing);
            }
            return sessionId;
        }

        // 创建新会话
        ChatSession session = new ChatSession()
                .setId(UUID.randomUUID().toString())
                .setUserId(UserContext.getUserId())
                .setKbIds(kbIds.toString())
                .setMessageCount(0);
        sessionRepository.save(session);

        log.info("ChatSessionService.getOrCreateSession sessionId={},userId={}", session.getId(), UserContext.getUserId());
        return session.getId();
    }

    /**
     * 保存一轮对话（用户问题 + 助手回答）
     */
    @Transactional
    public void saveMessage(String sessionId, String question, String answer,
                             String sourcesJson, int latencyMs) {
        // 保存用户消息
        ChatMessage userMsg = new ChatMessage()
                .setSessionId(sessionId)
                .setRole("USER")
                .setContent(question);
        messageRepository.save(userMsg);

        // 保存助手回答
        ChatMessage assistantMsg = new ChatMessage()
                .setSessionId(sessionId)
                .setRole("ASSISTANT")
                .setContent(answer)
                .setSources(sourcesJson)
                .setLatencyMs(latencyMs);
        messageRepository.save(assistantMsg);

        // 更新会话消息数和活跃时间
        ChatSession session = sessionRepository.findById(sessionId);
        if (Objects.nonNull(session)) {
            session.setMessageCount(session.getMessageCount() + 2);
            session.setLastActiveAt(LocalDateTime.now());
            if (Objects.isNull(session.getTitle()) && question.length() > 0) {
                session.setTitle(question.substring(0, Math.min(50, question.length())));
            }
            sessionRepository.updateById(session);
        }
    }

    /**
     * 获取会话历史（用于多轮对话上下文）
     * 最近 MAX_HISTORY_ROUNDS 轮，不含当前问题
     */
    public List<ChatMessage> getHistory(String sessionId) {
        List<ChatMessage> all = messageRepository
                .findBySessionIdOrderByCreatedAtAsc(sessionId);

        // 取最近的 N 轮（N * 2 条消息：用户 + 助手）
        int maxMessages = MAX_HISTORY_ROUNDS * 2;
        if (all.size() > maxMessages) {
            all = all.subList(all.size() - maxMessages, all.size());
        }
        return all;
    }

    /**
     * 获取当前用户的会话列表（按最近活跃时间倒序）
     */
    public List<ChatSession> listUserSessions(Long userId) {
        return sessionRepository.findByUserIdAndIsDeletedFalseOrderByLastActiveAtDesc(userId);
    }

    /**
     * 获取指定会话的全部消息列表（按创建时间升序）
     */
    public List<ChatMessage> getSessionMessages(String sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }
}