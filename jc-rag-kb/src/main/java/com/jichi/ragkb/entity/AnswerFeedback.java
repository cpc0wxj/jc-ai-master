package com.jichi.ragkb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 用户回答反馈实体类
 * 用于记录用户对 AI 回答的点赞/点踩，便于效果评估
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("kb_answer_feedback")
public class AnswerFeedback {
    /**
     * 反馈主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 关联的对话消息ID
     */
    private Long messageId;
    /**
     * 反馈用户ID
     */
    private Long userId;
    /**
     * 反馈类型：1=有用 -1=无用
     */
    private Short feedback;
    /**
     * 可选的文字反馈
     */
    private String comment;
    /**
     * 反馈创建时间
     */
    private LocalDateTime createdAt;
}
