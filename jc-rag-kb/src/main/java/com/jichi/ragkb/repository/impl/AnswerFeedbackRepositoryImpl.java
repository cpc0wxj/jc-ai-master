package com.jichi.ragkb.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.jichi.ragkb.entity.AnswerFeedback;
import com.jichi.ragkb.mapper.AnswerFeedbackMapper;
import com.jichi.ragkb.repository.AnswerFeedbackRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户回答反馈 Repository 实现
 */
@Repository
public class AnswerFeedbackRepositoryImpl extends ServiceImpl<AnswerFeedbackMapper, AnswerFeedback> implements AnswerFeedbackRepository {
    @Override
    public boolean save(AnswerFeedback entity) {
        return super.save(entity);
    }

    @Override
    public Optional<AnswerFeedback> findByMessageIdAndUserId(Long messageId, Long userId) {
        AnswerFeedback result = getOne(new LambdaQueryWrapper<AnswerFeedback>()
                .eq(AnswerFeedback::getMessageId, messageId)
                .eq(AnswerFeedback::getUserId, userId));
        return Optional.ofNullable(result);
    }
}
