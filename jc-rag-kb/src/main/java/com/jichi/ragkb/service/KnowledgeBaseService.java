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
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository kbRepository;
    private final KbPermissionRepository permissionRepository;
    private final KbDocumentRepository documentRepository;
    private final DocChunkRepository chunkRepository;
    private final MinioStorageService minioService;
    private final IndexService indexService;

    @Transactional
    public KnowledgeBase create(KnowledgeBaseCreateRequest req) {
        KnowledgeBase kb = new KnowledgeBase()
                .setName(req.getName())
                .setDescription(req.getDescription())
                .setDepartmentId(req.getDepartmentId())
                .setIsPublic(req.getIsPublic())
                .setCreatedBy(UserContext.getUserId());
        kbRepository.save(kb);

        // 创建者自动获得 ADMIN 权限
        KbPermission perm = new KbPermission()
                .setKbId(kb.getId())
                .setSubjectType("USER")
                .setSubjectId(String.valueOf(UserContext.getUserId()))
                .setPermission("ADMIN")
                .setGrantedBy(UserContext.getUserId());
        permissionRepository.save(perm);

        log.info("KnowledgeBaseService.create id={},name={},creator={}", kb.getId(), kb.getName(), UserContext.getUserId());
        return kb;
    }

    /**
     * 查询当前用户可访问的知识库列表，并附带权限级别
     */
    public List<KnowledgeBaseVO> listAccessible() {
        String dept = UserContext.getDepartmentId();
        String role = UserContext.getRole();
        String userId = String.valueOf(UserContext.getUserId());

        if ("ADMIN".equalsIgnoreCase(role)) {
            List<KnowledgeBase> kbList = kbRepository.findByIsDeletedFalse();
            return kbList.stream().map(kb -> toVO(kb, "ADMIN")).toList();
        }

        // 收集用户/部门的权限映射：kbId -> 最高权限
        Map<Long, String> permMap = new HashMap<>();

        permissionRepository.findBySubjectTypeAndSubjectId("DEPARTMENT", dept)
                .forEach(p -> permMap.merge(p.getKbId(), p.getPermission(), this::higherPermission));

        permissionRepository.findBySubjectTypeAndSubjectId("USER", userId)
                .forEach(p -> permMap.merge(p.getKbId(), p.getPermission(), this::higherPermission));

        // 公开库：没有显式权限的给 READ
        Set<Long> accessibleIds = new HashSet<>(permMap.keySet());
        kbRepository.findByIsPublicTrueAndIsDeletedFalse().forEach(kb -> {
            accessibleIds.add(kb.getId());
            permMap.putIfAbsent(kb.getId(), "READ");
        });

        if (accessibleIds.isEmpty()) {
            return List.of();
        }

        return kbRepository.findAllById(accessibleIds).stream()
                .filter(kb -> !kb.getIsDeleted())
                .map(kb -> toVO(kb, permMap.getOrDefault(kb.getId(), "READ")))
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
        String minioPath = minioService.upload(kbId, file);

        // 创建文档记录
        KbDocument doc = new KbDocument()
                .setKbId(kbId)
                .setFileName(fileName)
                .setFileType(detectFileType(fileName))
                .setFileSize(file.getSize())
                .setMinioPath(minioPath)
                .setUploadedBy(UserContext.getUserId());
        documentRepository.save(doc);

        // 异步提交索引任务
        indexService.submitIndexTask(doc.getId());

        log.info("KnowledgeBaseService.uploadDocument docId={},fileName={},kbId={}", doc.getId(), fileName, kbId);
        return doc;
    }

    /**
     * 删除文档：软删除文档记录 + 硬删除向量数据 + 删除 MinIO 文件
     */
    @Transactional
    public void deleteDocument(Long docId) {
        KbDocument doc = documentRepository.findById(docId);
        if (Objects.isNull(doc)) {
            throw new RuntimeException("文档不存在：" + docId);
        }

        // 软删除文档记录
        doc.setIsDeleted(true);
        documentRepository.updateById(doc);

        // 硬删除向量数据（向量数据不做软删除，占空间且不需要恢复）
        chunkRepository.deleteByDocId(docId);

        // 异步删除 MinIO 文件（不影响主流程）
        minioService.delete(doc.getMinioPath());

        log.info("KnowledgeBaseService.deleteDocument docId={},fileName={}", docId, doc.getFileName());
    }

    /**
     * 重建索引：递增版本号 → 提交新索引任务 → 新任务完成后旧分块自动清理
     */
    @Transactional
    public void reindex(Long docId) {
        KbDocument doc = documentRepository.findById(docId);
        if (Objects.isNull(doc)) {
            throw new RuntimeException("文档不存在：" + docId);
        }

        // 版本号递增，索引完成后旧版本分块会被清理
        doc.setVersion(doc.getVersion() + 1)
                .setStatus(KbDocument.DocumentStatus.PENDING)
                .setErrorMsg(null);
        documentRepository.updateById(doc);

        indexService.submitIndexTask(docId);
        log.info("KnowledgeBaseService.reindex docId={},newVersion={}", docId, doc.getVersion());
    }

    private KnowledgeBaseVO toVO(KnowledgeBase kb, String permission) {
        return new KnowledgeBaseVO()
                .setId(kb.getId())
                .setName(kb.getName())
                .setDescription(kb.getDescription())
                .setDepartmentId(kb.getDepartmentId())
                .setIsPublic(kb.getIsPublic())
                .setCreatedBy(kb.getCreatedBy())
                .setCreatedAt(kb.getCreatedAt())
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
