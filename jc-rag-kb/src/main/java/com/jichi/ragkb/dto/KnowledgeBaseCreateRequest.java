package com.jichi.ragkb.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 知识库创建请求
 */
@Getter
@Setter
@Accessors(chain = true)
public class KnowledgeBaseCreateRequest {
    private String name;
    private String description;
    private String departmentId;
    private Boolean isPublic = false;
}
