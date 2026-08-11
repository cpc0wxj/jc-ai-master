package com.jichi.ragkb.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 知识库视图对象
 */
@Getter
@Setter
@Accessors(chain = true)
public class KnowledgeBaseVO {
    private Long id;
    private String name;
    private String description;
    private String departmentId;
    private Boolean isPublic;
    private Long createdBy;
    private LocalDateTime createdAt;
    /**
     * 当前用户对该知识库的权限：ADMIN / WRITE / READ
     */
    private String permission;
}
