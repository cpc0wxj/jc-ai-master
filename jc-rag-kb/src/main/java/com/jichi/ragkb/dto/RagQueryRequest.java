package com.jichi.ragkb.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * RAG 查询请求
 */
@Getter
@Setter
@Accessors(chain = true)
public class RagQueryRequest {
    private String question;
    private List<Long> kbIds;
}
