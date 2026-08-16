package com.jichi.ragkb.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 对话请求
 */
@Getter
@Setter
@Accessors(chain = true)
public class ChatRequest {
    /**
     * 用户提问内容
     */
    private String question;
    /**
     * 查询的知识库ID列表
     */
    private List<Long> kbIds;
    /**
     * 会话ID，为空时创建新会话
     */
    private String sessionId;
}