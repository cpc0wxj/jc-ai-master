package com.jichi.ragkb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 知识库实体类
 * 用于管理各部门创建的知识库，一个部门可以创建多个知识库。
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("kb_knowledge_base")
public class KnowledgeBase {
    /**
     * 知识库主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 知识库名称
     */
    private String name;
    /**
     * 知识库描述
     */
    private String description;
    /**
     * 知识库所属部门 ID
     */
    private String departmentId;
    /**
     * 是否公开
     */
    private Boolean isPublic = false;
    /**
     * 创建者用户 ID
     */
    private Long createdBy;
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    /**
     * 最后更新时间
     */
    private LocalDateTime updatedAt;
    /**
     * 逻辑删除标识
     */
    private Boolean isDeleted = false;
}