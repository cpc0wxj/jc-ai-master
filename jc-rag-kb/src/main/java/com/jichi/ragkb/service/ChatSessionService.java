package com.jichi.ragkb.service;

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
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    /**
     * 最多保留的历史轮数（超出后截断旧消息）
     */
    private static final int MAX_HISTORY_ROUNDS = 5;

    /**
     * 获取已有会话，或创建新会话
     */
    @Transactional
    public String getOrCreateSession(String sessionId, List<Long> kbIds) {
        if (StringUtils.isNotBlank(sessionId)) {
            ChatSession chatSession = chatSessionRepository.findById(sessionId);
            if (Objects.nonNull(chatSession)) {
                chatSession.setLastActiveAt(LocalDateTime.now());
                chatSessionRepository.updateById(chatSession);
            }
            return sessionId;
        }

        // 创建新会话
        ChatSession session = new ChatSession()
                .setId(UUID.randomUUID().toString())
                .setUserId(UserContext.getUserId())
                .setKbIds(kbIds.toString())
                .setMessageCount(0);
        chatSessionRepository.save(session);

        log.info("ChatSessionService.getOrCreateSession sessionId={},userId={}", session.getId(), UserContext.getUserId());
        return session.getId();
    }

    /**
     * 保存一轮对话（用户问题 + 助手回答）
     */
    @Transactional
    public void saveMessage(String sessionId, String question, String answer, String sourcesJson, int latencyMs) {
        // 保存用户消息
        ChatMessage userMsg = new ChatMessage()
                .setSessionId(sessionId)
                .setRole("USER")
                .setContent(question);
        chatMessageRepository.save(userMsg);

        // 保存助手回答
        ChatMessage assistantMsg = new ChatMessage()
                .setSessionId(sessionId)
                .setRole("ASSISTANT")
                .setContent(answer)
                .setSources(sourcesJson)
                .setLatencyMs(latencyMs);
        chatMessageRepository.save(assistantMsg);

        // 更新会话消息数和活跃时间
        ChatSession chatSession = chatSessionRepository.findById(sessionId);
        if (Objects.nonNull(chatSession)) {
            chatSession.setMessageCount(chatSession.getMessageCount() + 2);
            chatSession.setLastActiveAt(LocalDateTime.now());
            if (Objects.isNull(chatSession.getTitle()) && question.length() > 0) {
                chatSession.setTitle(question.substring(0, Math.min(50, question.length())));
            }
            chatSessionRepository.updateById(chatSession);
        }
    }

    /**
     * 获取会话历史（用于多轮对话上下文）
     * 最近 MAX_HISTORY_ROUNDS 轮，不含当前问题
     */
    public List<ChatMessage> getHistory(String sessionId) {
        List<ChatMessage> chatMessageList = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);

        // 取最近的 N 轮（N * 2 条消息：用户 + 助手）
        int maxMessages = MAX_HISTORY_ROUNDS * 2;
        if (chatMessageList.size() > maxMessages) {
            chatMessageList = chatMessageList.subList(chatMessageList.size() - maxMessages, chatMessageList.size());
        }
        return chatMessageList;
    }

    /**
     * 获取当前用户的会话列表（按最近活跃时间倒序）
     */
    public List<ChatSession> listUserSessions(Long userId) {
        return chatSessionRepository.findByUserIdAndIsDeletedFalseOrderByLastActiveAtDesc(userId);
    }

    /**
     * 获取指定会话的全部消息列表（按创建时间升序）
     */
    public List<ChatMessage> getSessionMessages(String sessionId) {
        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }
}