package com.jichi.ragkb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 对话消息实体类
 * 每条记录是会话中的一条消息（用户提问或 AI 回答）
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("kb_chat_message")
public class ChatMessage {
    /**
     * 消息主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 所属会话ID
     */
    private String sessionId;
    /**
     * 消息角色：USER / ASSISTANT
     */
    private String role;
    /**
     * 消息内容
     */
    private String content;
    /**
     * 引用来源列表（JSON 格式，仅 ASSISTANT 消息有）
     */
    private String sources;
    /**
     * 生成耗时（毫秒）
     */
    private Integer latencyMs;
    /**
     * 用户反馈：1=好 -1=差 NULL=未反馈
     */
    private Short feedback;
    /**
     * 消息创建时间
     */
    private LocalDateTime createdAt;
}
