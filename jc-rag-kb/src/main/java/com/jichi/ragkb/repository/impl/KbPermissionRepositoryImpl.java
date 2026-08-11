package com.jichi.ragkb.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.jichi.ragkb.entity.KbPermission;
import com.jichi.ragkb.mapper.KbPermissionMapper;
import com.jichi.ragkb.repository.KbPermissionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 知识库权限 Repository 实现
 */
@Repository
public class KbPermissionRepositoryImpl extends ServiceImpl<KbPermissionMapper, KbPermission> implements KbPermissionRepository {
    @Override
    public boolean save(KbPermission entity) {
        return super.save(entity);
    }

    @Override
    public List<KbPermission> findBySubjectTypeAndSubjectId(String subjectType, String subjectId) {
        return list(new LambdaQueryWrapper<KbPermission>()
                .eq(KbPermission::getSubjectType, subjectType)
                .eq(KbPermission::getSubjectId, subjectId));
    }

    @Override
    public boolean existsByKbIdAndSubjectTypeAndSubjectId(Long kbId, String subjectType, String subjectId) {
        return count(new LambdaQueryWrapper<KbPermission>()
                .eq(KbPermission::getKbId, kbId)
                .eq(KbPermission::getSubjectType, subjectType)
                .eq(KbPermission::getSubjectId, subjectId)) > 0;
    }

    @Override
    public boolean existsByKbIdAndSubjectTypeAndSubjectIdAndPermissionIn(
            Long kbId, String subjectType, String subjectId, List<String> permissions) {
        return count(new LambdaQueryWrapper<KbPermission>()
                .eq(KbPermission::getKbId, kbId)
                .eq(KbPermission::getSubjectType, subjectType)
                .eq(KbPermission::getSubjectId, subjectId)
                .in(KbPermission::getPermission, permissions)) > 0;
    }

    @Override
    public List<KbPermission> findByKbId(Long kbId) {
        return list(new LambdaQueryWrapper<KbPermission>()
                .eq(KbPermission::getKbId, kbId));
    }

    @Override
    public boolean deleteByKbId(Long kbId) {
        return remove(new LambdaQueryWrapper<KbPermission>()
                .eq(KbPermission::getKbId, kbId));
    }
}
