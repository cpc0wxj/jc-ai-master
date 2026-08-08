package com.jichi.ragkb.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 知识库文档索引任务实体类用于管理文档的索引和重建索引任务
 */
@Getter
@Setter
@Entity
@Accessors(chain = true)
@Table(name = "kb_index_task")
public class IndexTask {
    /**
     * 索引任务主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /**
     * 待索引文档ID
     */
    @Column(name = "doc_id", nullable = false)
    private Long docId;
    /**
     * 任务类型包括首次索引和重建索引
     */
    @Column(name = "task_type", nullable = false, length = 20)
    private String taskType = "INDEX";
    /**
     * 索引任务当前处理状态
     */
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private TaskStatus status = TaskStatus.PENDING;
    /**
     * 任务已重试次数
     */
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;
    /**
     * 任务最大重试次数
     */
    @Column(name = "max_retry", nullable = false)
    private Integer maxRetry = 3;
    /**
     * 任务处理失败原因
     */
    @Column(name = "error_msg", columnDefinition = "TEXT")
    private String errorMsg;
    /**
     * 任务创建时间
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    /**
     * 任务开始处理时间
     */
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    /**
     * 任务完成时间
     */
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    /**
     * 索引任务状态枚举
     */
    public enum TaskStatus {
        /**
         * 待处理
         */
        PENDING,
        /**
         * 处理中
         */
        RUNNING,
        /**
         * 处理完成
         */
        DONE,
        /**
         * 处理失败
         */
        FAILED
    }

    /**
     * 判断当前任务是否可以重试
     */
    public boolean canRetry() {
        return retryCount < maxRetry && status == TaskStatus.FAILED;
    }
}