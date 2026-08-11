package com.jichi.ragkb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 对话会话实体类
 * 管理用户与 AI 的多轮对话会话，一个用户可以有多个会话
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("kb_chat_session")
public class ChatSession {
    /**
     * 会话主键ID（UUID）
     */
    @TableId(type = IdType.INPUT)
    private String id;
    /**
     * 所属用户ID
     */
    private Long userId;
    /**
     * 查询的知识库ID列表（JSON 数组字符串）
     */
    private String kbIds;
    /**
     * 会话标题（取第一条消息）
     */
    private String title;
    /**
     * 会话消息总数
     */
    private Integer messageCount = 0;
    /**
     * 会话创建时间
     */
    private LocalDateTime createdAt;
    /**
     * 最近活跃时间
     */
    private LocalDateTime lastActiveAt;
    /**
     * 逻辑删除标识
     */
    private Boolean isDeleted = false;
}
