package com.jichi.ragkb.service;

import cn.hutool.core.collection.CollStreamUtil;
import com.jichi.ragkb.dto.KnowledgeBaseCreateRequest;
import com.jichi.ragkb.dto.KnowledgeBaseVO;
import com.jichi.ragkb.entity.KbDocument;
import com.jichi.ragkb.entity.KbPermission;
import com.jichi.ragkb.entity.KnowledgeBase;
import com.jichi.ragkb.repository.DocChunkRepository;
import com.jichi.ragkb.repository.KbDocumentRepository;
import com.jichi.ragkb.repository.KbPermissionRepository;
import com.jichi.ragkb.repository.KnowledgeBaseRepository;
import com.jichi.ragkb.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * 知识库管理服务
 * 管理知识库的创建、查询、文档上传/删除/重建索引等操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KbPermissionRepository kbPermissionRepository;
    private final KbDocumentRepository kbDocumentRepository;
    private final DocChunkRepository docChunkRepository;
    private final MinioStorageService minioStorageService;
    private final IndexService indexService;

    /**
     * 权限等级映射
     * READ=1, WRITE=2, ADMIN=3，用于比较权限高低
     */
    private static final Map<String, Integer> PERM_LEVEL = Map.of("READ", 1, "WRITE", 2, "ADMIN", 3);

    /**
     * 创建知识库
     * 创建知识库记录后，自动为创建者授予 ADMIN 权限
     *
     * @param knowledgeBaseCreateRequest 知识库创建请求
     * @return 创建完成的知识库实体（含自动生成的 ID）
     */
    @Transactional
    public KnowledgeBase create(KnowledgeBaseCreateRequest knowledgeBaseCreateRequest) {
        KnowledgeBase knowledgeBase = new KnowledgeBase()
                .setName(knowledgeBaseCreateRequest.getName())
                .setDescription(knowledgeBaseCreateRequest.getDescription())
                .setDepartmentId(knowledgeBaseCreateRequest.getDepartmentId())
                .setIsPublic(knowledgeBaseCreateRequest.getIsPublic())
                .setCreatedBy(UserContext.getUserId());
        knowledgeBaseRepository.save(knowledgeBase);

        // 创建者自动获得 ADMIN 权限
        KbPermission kbPermission = new KbPermission()
                .setKbId(knowledgeBase.getId())
                .setSubjectType("USER")
                .setSubjectId(String.valueOf(UserContext.getUserId()))
                .setPermission("ADMIN")
                .setGrantedBy(UserContext.getUserId());
        kbPermissionRepository.save(kbPermission);

        log.info("KnowledgeBaseService.create id={},name={},creator={}", knowledgeBase.getId(), knowledgeBase.getName(), UserContext.getUserId());
        return knowledgeBase;
    }

    /**
     * 查询当前用户可访问的知识库列表，并附带权限级别
     * 可见范围:
     * DMIN 角色：可见全部知识库
     * 普通用户：部门权限 + 用户权限 + 公开库（READ 兜底）
     *
     * @return 知识库视图对象列表，附带当前用户的权限级别
     */
    public List<KnowledgeBaseVO> listAccessible() {
        String departmentId = UserContext.getDepartmentId();
        String role = UserContext.getRole();
        String userId = String.valueOf(UserContext.getUserId());

        // 系统管理员：可见全部知识库，统一赋予 ADMIN 权限
        if ("ADMIN".equalsIgnoreCase(role)) {
            List<KnowledgeBase> knowledgeBaseList = knowledgeBaseRepository.findByIsDeletedFalse();
            return CollStreamUtil.toList(knowledgeBaseList, knowledgeBase -> toVO(knowledgeBase, "ADMIN"));
        }

        // 收集用户/部门的权限映射：kbId -> 最高权限
        Map<Long, String> permissionMap = new HashMap<>();
        List<KbPermission> kbPermissionList = kbPermissionRepository.findBySubjectTypeAndSubjectId("DEPARTMENT", departmentId);
        for (KbPermission permission : kbPermissionList) {
            permissionMap.merge(permission.getKbId(), permission.getPermission(), this::higherPermission);
        }

        kbPermissionList = kbPermissionRepository.findBySubjectTypeAndSubjectId("USER", userId);
        for (KbPermission kbPermission : kbPermissionList) {
            permissionMap.merge(kbPermission.getKbId(), kbPermission.getPermission(), this::higherPermission);
        }

        // 公开库：没有显式权限的给 READ
        Set<Long> accessibleIdList = new HashSet<>(permissionMap.keySet());
        List<KnowledgeBase> knowledgeBaseList = knowledgeBaseRepository.findByIsPublicTrueAndIsDeletedFalse();
        for (KnowledgeBase knowledgeBase : knowledgeBaseList) {
            accessibleIdList.add(knowledgeBase.getId());
            permissionMap.putIfAbsent(knowledgeBase.getId(), "READ");
        }

        if (CollectionUtils.isEmpty(accessibleIdList)) {
            return List.of();
        }

        knowledgeBaseList = knowledgeBaseRepository.findAllById(accessibleIdList);
        return CollStreamUtil.toList(knowledgeBaseList, temp -> {
            if (!Objects.equals(temp.getIsDeleted(), Boolean.FALSE)) {
                return null;
            }
            return toVO(temp, permissionMap.getOrDefault(temp.getId(), "READ"));
        });
    }

    /**
     * 上传文档：存 MinIO → 创建文档记录 → 提交索引任务
     *
     * @param kbId 知识库 ID
     * @param file 上传的文件
     * @return 文档记录（含自动生成的 ID）
     */
    @Transactional
    public KbDocument uploadDocument(Long kbId, MultipartFile file) {
        validateFileType(file.getOriginalFilename());

        // 上传到 MinIO
        String minioPath = minioStorageService.upload(kbId, file);

        // 创建文档记录
        KbDocument kbDocument = new KbDocument()
                .setKbId(kbId)
                .setFileName(file.getOriginalFilename())
                .setFileType(detectFileType(file.getOriginalFilename()))
                .setFileSize(file.getSize())
                .setMinioPath(minioPath)
                .setUploadedBy(UserContext.getUserId());
        kbDocumentRepository.save(kbDocument);

        // 注册事务完成后的回调 - 确保主事务提交后再执行索引
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        // 事务提交后异步提交索引任务
                        indexService.submitIndexTask(kbDocument.getId());
                    }
                }
        );

        log.info("KnowledgeBaseService.uploadDocument docId={},fileName={},kbId={}", kbDocument.getId(), file.getOriginalFilename(), kbId);
        return kbDocument;
    }

    /**
     * 删除文档：软删除文档记录 + 硬删除向量数据 + 删除 MinIO 文件
     *
     * @param docId 文档 ID
     */
    @Transactional
    public void deleteDocument(Long docId) {
        // 查文档
        KbDocument kbDocument = kbDocumentRepository.findById(docId);
        String minioPath = Optional.ofNullable(kbDocument).map(KbDocument::getMinioPath).orElse(null);
        // 软删除文档记录
        kbDocument = new KbDocument()
                .setId(docId)
                .setIsDeleted(true);
        kbDocumentRepository.updateById(kbDocument);
        // 硬删除向量数据（向量数据不做软删除，占空间且不需要恢复）
        docChunkRepository.deleteByDocId(docId);

        // 删除 MinIO 文件
        minioStorageService.delete(minioPath);

        log.info("KnowledgeBaseService.deleteDocument docId={}", docId);
    }

    /**
     * 重建索引：递增版本号 → 提交新索引任务 → 新任务完成后旧分块自动清理
     *
     * @param docId 文档 ID
     */
    @Transactional
    public void reindex(Long docId) {
        KbDocument kbDocument = kbDocumentRepository.findById(docId);
        if (Objects.isNull(kbDocument)) {
            throw new RuntimeException("文档不存在：" + docId);
        }

        // version 不在此处递增——统一由 IndexService.doIndex 递增并写入同版本 chunk，
        // 提前 +1 会导致 version 跳号，且检索 SQL（doc_version = version）在索引完成前匹配不上旧 chunk
        kbDocument.setVersion(null)
                .setStatus(KbDocument.DocumentStatus.PENDING)
                .setErrorMsg("");
        kbDocumentRepository.updateById(kbDocument);

        indexService.submitIndexTask(docId);
        log.info("KnowledgeBaseService.reindex docId={}", docId);
    }

    /**
     * 将知识库实体转换为视图对象
     *
     * @param knowledgeBase 知识库实体
     * @param permission    当前用户对该知识库的权限级别
     * @return 知识库视图对象
     */
    private KnowledgeBaseVO toVO(KnowledgeBase knowledgeBase, String permission) {
        return new KnowledgeBaseVO()
                .setId(knowledgeBase.getId())
                .setName(knowledgeBase.getName())
                .setDescription(knowledgeBase.getDescription())
                .setDepartmentId(knowledgeBase.getDepartmentId())
                .setIsPublic(knowledgeBase.getIsPublic())
                .setCreatedBy(knowledgeBase.getCreatedBy())
                .setCreatedAt(knowledgeBase.getCreatedAt())
                .setPermission(permission);
    }

    /**
     * 比较两个权限，返回较高的那个
     *
     * @param a 权限 A
     * @param b 权限 B
     * @return 较高的权限
     */
    private String higherPermission(String a, String b) {
        return PERM_LEVEL.getOrDefault(a, 0) >= PERM_LEVEL.getOrDefault(b, 0) ? a : b;
    }

    /**
     * 校验文件类型是否受支持
     * 目前支持：PDF、DOCX、MD、TXT
     *
     * @param fileName 文件名
     */
    private void validateFileType(String fileName) {
        if (Objects.isNull(fileName)) {
            throw new RuntimeException("文件名不能为空");
        }
        String lower = fileName.toLowerCase();
        if (!lower.endsWith(".pdf")
                && !lower.endsWith(".docx")
                && !lower.endsWith(".md")
                && !lower.endsWith(".txt")) {
            throw new RuntimeException("不支持的文件类型，目前支持：PDF、DOCX、MD、TXT");
        }
    }

    /**
     * 根据文件扩展名识别文件类型
     *
     * @param fileName 文件名
     * @return 文件类型字符串（PDF/DOCX/MD/TXT/UNKNOWN）
     */
    private String detectFileType(String fileName) {
        if (Objects.isNull(fileName)) {
            return "UNKNOWN";
        }
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return "PDF";
        }
        if (lower.endsWith(".docx")) {
            return "DOCX";
        }
        if (lower.endsWith(".md")) {
            return "MD";
        }
        if (lower.endsWith(".txt")) {
            return "TXT";
        }
        return "UNKNOWN";
    }
}