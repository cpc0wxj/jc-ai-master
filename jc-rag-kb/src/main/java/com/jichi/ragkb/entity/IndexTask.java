package com.jichi.ragkb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 知识库文档索引任务实体类用于管理文档的索引和重建索引任务
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("kb_index_task")
public class IndexTask {
    /**
     * 索引任务主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 待索引文档ID
     */
    private Long docId;
    /**
     * 任务类型包括首次索引和重建索引
     */
    private String taskType = "INDEX";
    /**
     * 索引任务当前处理状态
     */
    private TaskStatus status = TaskStatus.PENDING;
    /**
     * 任务已重试次数
     */
    private Integer retryCount = 0;
    /**
     * 任务最大重试次数
     */
    private Integer maxRetry = 3;
    /**
     * 任务处理失败原因
     */
    private String errorMsg;
    /**
     * 任务创建时间
     */
    private LocalDateTime createdAt;
    /**
     * 任务开始处理时间
     */
    private LocalDateTime startedAt;
    /**
     * 任务完成时间
     */
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