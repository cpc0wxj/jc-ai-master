package com.jichi.ragkb.repository;

import com.jichi.ragkb.entity.AnswerFeedback;

import java.util.Optional;

/**
 * 用户回答反馈 Repository 接口
 */
public interface AnswerFeedbackRepository {
    /**
     * 新增反馈（INSERT）
     */
    boolean save(AnswerFeedback entity);

    /**
     * 根据消息ID和用户ID查询反馈
     */
    Optional<AnswerFeedback> findByMessageIdAndUserId(Long messageId, Long userId);
}
