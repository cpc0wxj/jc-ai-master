package com.jichi.ragkb.service;

import com.jichi.ragkb.entity.AnswerFeedback;
import com.jichi.ragkb.entity.ChatMessage;
import com.jichi.ragkb.repository.AnswerFeedbackRepository;
import com.jichi.ragkb.repository.ChatMessageRepository;
import com.jichi.ragkb.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 用户反馈服务
 * 处理用户对 AI 回答的点赞/点踩，差评自动候选加入评估数据集
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {
    private final AnswerFeedbackRepository answerFeedbackRepository;

    private final ChatMessageRepository chatMessageRepository;

    /**
     * 提交用户反馈（点赞/点踩）
     * 对于差评，自动提取问题加入候选评估数据集（人工审核后正式纳入）
     */
    @Transactional
    public void submitFeedback(Long messageId, int feedback, String comment) {
        ChatMessage chatMessage = chatMessageRepository.findById(messageId);
        if (Objects.isNull(chatMessage)) {
            throw new RuntimeException("消息不存在");
        }

        // 同一用户对同一消息只保留一条反馈，重复提交则覆盖
        Long userId = UserContext.getUserId();
        AnswerFeedback answerFeedback = answerFeedbackRepository.findByMessageIdAndUserId(messageId, userId).orElse(null);
        if (Objects.nonNull(answerFeedback)) {
            answerFeedback.setFeedback((short) feedback)
                    .setComment(comment);
            answerFeedbackRepository.save(answerFeedback);
        } else {
            AnswerFeedback newFb = new AnswerFeedback()
                    .setMessageId(messageId)
                    .setUserId(userId)
                    .setFeedback((short) feedback)
                    .setComment(comment);
            answerFeedbackRepository.save(newFb);
        }

        // 更新消息的 feedback 字段
        chatMessage.setFeedback((short) feedback);
        chatMessageRepository.updateById(chatMessage);

        // 差评：把这个问题加入评估候选（人工审核后加入正式评估集）
        if (feedback == -1) {
            log.info("FeedbackService.submitFeedback negativeFeedback,messageId={}", messageId);
        }

        log.info("FeedbackService.submitFeedback messageId={},feedback={},userId={}",
                messageId, feedback, UserContext.getUserId());
    }
}