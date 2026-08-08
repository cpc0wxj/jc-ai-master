package com.jichi.ragkb.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 知识库实体类
 * 用于管理各部门创建的知识库，一个部门可以创建多个知识库。
 * 对应数据库表
 */
@Getter
@Setter
@Entity
@Table(name = "kb_knowledge_base")
public class KnowledgeBase {
    /**
     * 知识库主键 ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /**
     * 知识库名称
     * 不能为空，最大长度为 100 个字符
     */
    @Column(nullable = false, length = 100)
    private String name;
    /**
     * 知识库描述
     */
    @Column(columnDefinition = "TEXT")
    private String description;
    /**
     * 知识库所属部门 ID
     * 不能为空，最大长度为 50 个字符。
     */
    @Column(name = "department_id", nullable = false, length = 50)
    private String departmentId;
    /**
     * 是否公开
     * true：对所有用户开放
     * false：仅授权用户可访问
     */
    @Column(name = "is_public", nullable = false)
    private Boolean isPublic = false;
    /**
     * 创建者用户 ID
     */
    @Column(name = "created_by", nullable = false)
    private Long createdBy;
    /**
     * 创建时间
     * 实体首次持久化时由 Hibernate 自动设置，后续更新时不会修改。
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    /**
     * 最后更新时间
     * 实体创建或更新时由 Hibernate 自动设置。
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    /**
     * 逻辑删除标识
     * true 已删除
     * false 未删除
     */
    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}