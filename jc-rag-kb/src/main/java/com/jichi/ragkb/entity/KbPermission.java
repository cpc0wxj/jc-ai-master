package com.jichi.ragkb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 知识库权限实体类
 * 管理用户或部门对知识库的访问权限（READ / WRITE / ADMIN）
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("kb_permission")
public class KbPermission {
    /**
     * 权限主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 知识库ID
     */
    private Long kbId;
    /**
     * 授权主体类型：USER / DEPARTMENT
     */
    private String subjectType;
    /**
     * 授权主体ID（用户ID 或部门ID）
     */
    private String subjectId;
    /**
     * 权限级别：READ / WRITE / ADMIN
     */
    private String permission;
    /**
     * 授权人用户ID
     */
    private Long grantedBy;
    /**
     * 授权时间
     */
    private LocalDateTime grantedAt;
}