package com.jichi.ragkb.controller;

import com.jichi.ragkb.dto.ApiResponse;
import com.jichi.ragkb.dto.DocumentUploadResponse;
import com.jichi.ragkb.dto.IndexStatusResponse;
import com.jichi.ragkb.dto.KnowledgeBaseCreateRequest;
import com.jichi.ragkb.dto.KnowledgeBaseVO;
import com.jichi.ragkb.entity.IndexTask;
import com.jichi.ragkb.entity.KbDocument;
import com.jichi.ragkb.entity.KnowledgeBase;
import com.jichi.ragkb.repository.IndexTaskRepository;
import com.jichi.ragkb.repository.KbDocumentRepository;
import com.jichi.ragkb.service.KnowledgeBaseService;
import com.jichi.ragkb.service.MinioStorageService;
import com.jichi.ragkb.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * 知识库管理接口
 * 提供知识库创建/查询、文档上传/删除/下载/重建索引等操作
 */
@RestController
@RequestMapping("/api/v1/kb")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final PermissionService permissionService;
    private final KnowledgeBaseService kbService;
    private final KbDocumentRepository documentRepository;
    private final IndexTaskRepository taskRepository;
    private final MinioStorageService minioService;

    /**
     * 查询当前用户可访问的知识库列表（含权限级别）
     */
    @GetMapping
    public ApiResponse<List<KnowledgeBaseVO>> list() {
        return ApiResponse.ok(kbService.listAccessible());
    }

    /**
     * 创建知识库
     */
    @PostMapping
    public ApiResponse<KnowledgeBase> create(@RequestBody KnowledgeBaseCreateRequest req) {
        return ApiResponse.ok(kbService.create(req));
    }

    /**
     * 上传文档到知识库
     */
    @PostMapping("/{kbId}/documents")
    public ApiResponse<DocumentUploadResponse> upload(
            @PathVariable Long kbId,
            @RequestParam("file") MultipartFile file) {
        permissionService.requireWrite(kbId);
        KbDocument doc = kbService.uploadDocument(kbId, file);
        return ApiResponse.ok(DocumentUploadResponse.submitted(doc.getId(), doc.getFileName()));
    }

    /**
     * 查询文档索引状态（前端轮询用）
     */
    @GetMapping("/{kbId}/documents/{docId}/status")
    public ApiResponse<IndexStatusResponse> getStatus(
            @PathVariable Long kbId,
            @PathVariable Long docId) {
        permissionService.requireRead(kbId);

        KbDocument doc = documentRepository.findById(docId);
        if (Objects.isNull(doc)) {
            throw new RuntimeException("文档不存在");
        }

        // 查最新的索引任务（可能有重试）
        IndexTask latestTask = taskRepository.findTopByDocIdOrderByCreatedAtDesc(docId);

        IndexStatusResponse resp = new IndexStatusResponse()
                .setDocId(doc.getId())
                .setFileName(doc.getFileName())
                .setStatus(doc.getStatus().name())
                .setErrorMsg(doc.getErrorMsg())
                .setChunkCount(doc.getChunkCount())
                .setTokenCount(doc.getTokenCount())
                .setIndexedAt(Objects.nonNull(doc.getIndexedAt()) ? doc.getIndexedAt().toString() : null)
                .setRetryCount(Objects.nonNull(latestTask) ? latestTask.getRetryCount() : 0);
        return ApiResponse.ok(resp);
    }

    /**
     * 查询知识库的文档列表
     */
    @GetMapping("/{kbId}/documents")
    public ApiResponse<List<KbDocument>> listDocuments(@PathVariable Long kbId) {
        permissionService.requireRead(kbId);
        List<KbDocument> docs = documentRepository.findByKbIdAndIsDeletedFalse(kbId);
        return ApiResponse.ok(docs);
    }

    /**
     * 删除文档
     */
    @DeleteMapping("/{kbId}/documents/{docId}")
    public ApiResponse<Void> deleteDocument(
            @PathVariable Long kbId,
            @PathVariable Long docId) {
        permissionService.requireWrite(kbId);
        kbService.deleteDocument(docId);
        return ApiResponse.ok(null);
    }

    /**
     * 下载原始文件
     */
    @GetMapping("/{kbId}/documents/{docId}/download")
    public ResponseEntity<byte[]> download(
            @PathVariable Long kbId,
            @PathVariable Long docId) {
        permissionService.requireRead(kbId);
        KbDocument doc = documentRepository.findById(docId);
        if (Objects.isNull(doc)) {
            throw new RuntimeException("文档不存在");
        }
        byte[] content = minioService.download(doc.getMinioPath());
        String encodedName = URLEncoder.encode(doc.getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(content);
    }

    /**
     * 重建索引（文档内容更新后触发）
     */
    @PostMapping("/{kbId}/documents/{docId}/reindex")
    public ApiResponse<String> reindex(
            @PathVariable Long kbId,
            @PathVariable Long docId) {
        permissionService.requireWrite(kbId);
        kbService.reindex(docId);
        return ApiResponse.ok("重建索引任务已提交，请通过 /status 接口查询进度");
    }
}
