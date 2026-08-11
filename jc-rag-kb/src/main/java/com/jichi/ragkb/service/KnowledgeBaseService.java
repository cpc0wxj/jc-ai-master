package com.jichi.ragkb.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

    @Transactional
    public KnowledgeBase create(KnowledgeBaseCreateRequest req) {
        KnowledgeBase knowledgeBase = new KnowledgeBase()
                .setName(req.getName())
                .setDescription(req.getDescription())
                .setDepartmentId(req.getDepartmentId())
                .setIsPublic(req.getIsPublic())
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
     */
    public List<KnowledgeBaseVO> listAccessible() {
        String dept = UserContext.getDepartmentId();
        String role = UserContext.getRole();
        String userId = String.valueOf(UserContext.getUserId());

        if ("ADMIN".equalsIgnoreCase(role)) {
            List<KnowledgeBase> knowledgeBaseList = knowledgeBaseRepository.findByIsDeletedFalse();
            return knowledgeBaseList.stream().map(knowledgeBase -> toVO(knowledgeBase, "ADMIN")).toList();
        }

        // 收集用户/部门的权限映射：kbId -> 最高权限
        Map<Long, String> permMap = new HashMap<>();

        kbPermissionRepository.findBySubjectTypeAndSubjectId("DEPARTMENT", dept)
                .forEach(kbPermission -> permMap.merge(kbPermission.getKbId(), kbPermission.getPermission(), this::higherPermission));

        kbPermissionRepository.findBySubjectTypeAndSubjectId("USER", userId)
                .forEach(kbPermission -> permMap.merge(kbPermission.getKbId(), kbPermission.getPermission(), this::higherPermission));

        // 公开库：没有显式权限的给 READ
        Set<Long> accessibleIds = new HashSet<>(permMap.keySet());
        knowledgeBaseRepository.findByIsPublicTrueAndIsDeletedFalse().forEach(knowledgeBase -> {
            accessibleIds.add(knowledgeBase.getId());
            permMap.putIfAbsent(knowledgeBase.getId(), "READ");
        });

        if (accessibleIds.isEmpty()) {
            return List.of();
        }

        return knowledgeBaseRepository.findAllById(accessibleIds).stream()
                .filter(knowledgeBase -> !knowledgeBase.getIsDeleted())
                .map(knowledgeBase -> toVO(knowledgeBase, permMap.getOrDefault(knowledgeBase.getId(), "READ")))
                .toList();
    }

    /**
     * 上传文档：存 MinIO → 创建文档记录 → 提交索引任务
     *
     * @return 文档记录（含自动生成的 ID）
     */
    @Transactional
    public KbDocument uploadDocument(Long kbId, MultipartFile file) {
        String fileName = file.getOriginalFilename();
        validateFileType(fileName);

        // 上传到 MinIO
        String minioPath = minioStorageService.upload(kbId, file);

        // 创建文档记录
        KbDocument kbDocument = new KbDocument()
                .setKbId(kbId)
                .setFileName(fileName)
                .setFileType(detectFileType(fileName))
                .setFileSize(file.getSize())
                .setMinioPath(minioPath)
                .setUploadedBy(UserContext.getUserId());
        kbDocumentRepository.save(kbDocument);

        // 异步提交索引任务
        indexService.submitIndexTask(kbDocument.getId());

        log.info("KnowledgeBaseService.uploadDocument docId={},fileName={},kbId={}", kbDocument.getId(), fileName, kbId);
        return kbDocument;
    }

    /**
     * 删除文档：软删除文档记录 + 硬删除向量数据 + 删除 MinIO 文件
     */
    @Transactional
    public void deleteDocument(Long docId) {
        KbDocument kbDocument = kbDocumentRepository.findById(docId);
        if (Objects.isNull(kbDocument)) {
            throw new RuntimeException("文档不存在：" + docId);
        }

        // 软删除文档记录
        kbDocument.setIsDeleted(true);
        kbDocumentRepository.updateById(kbDocument);

        // 硬删除向量数据（向量数据不做软删除，占空间且不需要恢复）
        docChunkRepository.deleteByDocId(docId);

        // 异步删除 MinIO 文件（不影响主流程）
        minioStorageService.delete(kbDocument.getMinioPath());

        log.info("KnowledgeBaseService.deleteDocument docId={},fileName={}", docId, kbDocument.getFileName());
    }

    /**
     * 重建索引：递增版本号 → 提交新索引任务 → 新任务完成后旧分块自动清理
     */
    @Transactional
    public void reindex(Long docId) {
        KbDocument kbDocument = kbDocumentRepository.findById(docId);
        if (Objects.isNull(kbDocument)) {
            throw new RuntimeException("文档不存在：" + docId);
        }

        // 版本号递增，索引完成后旧版本分块会被清理
        kbDocument.setVersion(kbDocument.getVersion() + 1)
                .setStatus(KbDocument.DocumentStatus.PENDING)
                .setErrorMsg(null);
        kbDocumentRepository.updateById(kbDocument);

        indexService.submitIndexTask(docId);
        log.info("KnowledgeBaseService.reindex docId={},newVersion={}", docId, kbDocument.getVersion());
    }

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

    private static final Map<String, Integer> PERM_LEVEL = Map.of(
            "READ", 1, "WRITE", 2, "ADMIN", 3);

    private String higherPermission(String a, String b) {
        return PERM_LEVEL.getOrDefault(a, 0) >= PERM_LEVEL.getOrDefault(b, 0) ? a : b;
    }

    private void validateFileType(String fileName) {
        if (Objects.isNull(fileName)) {
            throw new RuntimeException("文件名不能为空");
        }
        String lower = fileName.toLowerCase();
        if (!lower.endsWith(".pdf") && !lower.endsWith(".docx") &&
            !lower.endsWith(".md")  && !lower.endsWith(".txt")) {
            throw new RuntimeException("不支持的文件类型，目前支持：PDF、DOCX、MD、TXT");
        }
    }

    private String detectFileType(String fileName) {
        if (Objects.isNull(fileName)) {
            return "UNKNOWN";
        }
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf"))  return "PDF";
        if (lower.endsWith(".docx")) return "DOCX";
        if (lower.endsWith(".md"))   return "MD";
        if (lower.endsWith(".txt"))  return "TXT";
        return "UNKNOWN";
    }
}