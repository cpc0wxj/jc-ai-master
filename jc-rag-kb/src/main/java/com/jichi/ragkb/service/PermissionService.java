package com.jichi.ragkb.service;

import com.jichi.ragkb.entity.KnowledgeBase;
import com.jichi.ragkb.exception.BizException;
import com.jichi.ragkb.repository.KbPermissionRepository;
import com.jichi.ragkb.repository.KnowledgeBaseRepository;
import com.jichi.ragkb.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 权限服务
 * 校验用户对知识库的读/写权限，管理员直接放行
 */
@Service
@RequiredArgsConstructor
public class PermissionService {
    private final KbPermissionRepository kbPermissionRepository;

    private final KnowledgeBaseRepository knowledgeBaseRepository;

    /**
     * 检查当前用户对知识库是否有读权限
     */
    public void requireRead(Long kbId) {
        if (UserContext.isAdmin()) {
            return;
        }

        // 知识库是公开的——直接放行
        KnowledgeBase knowledgeBase = knowledgeBaseRepository.findById(kbId);
        Boolean isPublic = Optional.ofNullable(knowledgeBase).map(KnowledgeBase::getIsPublic).orElse(null);
        if (Objects.equals(isPublic, Boolean.TRUE)) {
            return;
        }

        // 检查用户或部门权限
        boolean flag1 = kbPermissionRepository.existsByKbIdAndSubjectTypeAndSubjectId(kbId, "USER", String.valueOf(UserContext.getUserId()));
        boolean flag2= kbPermissionRepository.existsByKbIdAndSubjectTypeAndSubjectId(kbId, "DEPARTMENT", UserContext.getDepartmentId());

        if (!flag1 && !flag2) {
            throw BizException.forbidden("无权访问该知识库");
        }
    }

    /**
     * 检查当前用户对知识库是否有写权限
     */
    public void requireWrite(Long kbId) {
        if (UserContext.isAdmin()) {
            return;
        }

        boolean flag1 = kbPermissionRepository.existsByKbIdAndSubjectTypeAndSubjectIdAndPermissionIn(kbId, "USER", String.valueOf(UserContext.getUserId()), List.of("WRITE", "ADMIN"));
        boolean flag2 = kbPermissionRepository.existsByKbIdAndSubjectTypeAndSubjectIdAndPermissionIn(kbId, "DEPARTMENT", UserContext.getDepartmentId(), List.of("WRITE", "ADMIN"));

        if (!flag1 && !flag2) {
            throw BizException.forbidden("无文档管理权限");
        }
    }
}