package com.jichi.ragkb.controller;

import com.jichi.ragkb.dto.*;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 知识库管理接口
 * 提供知识库创建/查询、文档上传/删除/下载/重建索引等操作
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/kb")
public class KnowledgeBaseController {
    private final PermissionService permissionService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final KbDocumentRepository kbDocumentRepository;
    private final IndexTaskRepository indexTaskRepository;
    private final MinioStorageService minioStorageService;

    /**
     * 查询当前用户可访问的知识库列表（含权限级别）
     */
    @GetMapping
    public ApiResponse<List<KnowledgeBaseVO>> list() {
        List<KnowledgeBaseVO> knowledgeBaseVOList = knowledgeBaseService.listAccessible();
        return ApiResponse.ok(knowledgeBaseVOList);
    }

    /**
     * 创建知识库
     */
    @PostMapping
    public ApiResponse<KnowledgeBase> create(@RequestBody KnowledgeBaseCreateRequest knowledgeBaseCreateRequest) {
        KnowledgeBase knowledgeBase = knowledgeBaseService.create(knowledgeBaseCreateRequest);
        return ApiResponse.ok(knowledgeBase);
    }

    /**
     * 上传文档到知识库
     */
    @PostMapping("/{kbId}/documents")
    public ApiResponse<DocumentUploadResponse> upload(@PathVariable Long kbId, @RequestParam("file") MultipartFile file) {
        permissionService.requireWrite(kbId);
        KbDocument kbDocument = knowledgeBaseService.uploadDocument(kbId, file);
        DocumentUploadResponse documentUploadResponse = new DocumentUploadResponse()
                .setDocId(kbDocument.getId())
                .setFileName(kbDocument.getFileName())
                .setStatus("PENDING")
                .setMessage("文档已上传，正在后台索引，请通过 /status 接口查询进度");
        return ApiResponse.ok(documentUploadResponse);
    }

    /**
     * 查询文档索引状态（前端轮询用）
     */
    @GetMapping("/{kbId}/documents/{docId}/status")
    public ApiResponse<IndexStatusResponse> getStatus(@PathVariable Long kbId, @PathVariable Long docId) {
        permissionService.requireRead(kbId);

        KbDocument kbDocument = kbDocumentRepository.findById(docId);
        if (Objects.isNull(kbDocument)) {
            throw new RuntimeException("文档不存在");
        }

        // 查最新的索引任务（可能有重试）
        IndexTask indexTask = indexTaskRepository.findTopByDocIdOrderByCreatedAtDesc(docId);

        IndexStatusResponse indexStatusResponse = new IndexStatusResponse()
                .setDocId(kbDocument.getId())
                .setFileName(kbDocument.getFileName())
                .setStatus(kbDocument.getStatus().name())
                .setErrorMsg(kbDocument.getErrorMsg())
                .setChunkCount(kbDocument.getChunkCount())
                .setTokenCount(kbDocument.getTokenCount())
                .setIndexedAt(Optional.ofNullable(kbDocument.getIndexedAt()).map(LocalDateTime::toString).orElse(null))
                .setRetryCount(Optional.ofNullable(indexTask).map(IndexTask::getRetryCount).orElse(0));
        return ApiResponse.ok(indexStatusResponse);
    }

    /**
     * 查询知识库的文档列表
     */
    @GetMapping("/{kbId}/documents")
    public ApiResponse<List<KbDocument>> listDocuments(@PathVariable Long kbId) {
        permissionService.requireRead(kbId);
        List<KbDocument> kbDocumentList = kbDocumentRepository.findByKbIdAndIsDeletedFalse(kbId);
        return ApiResponse.ok(kbDocumentList);
    }

    /**
     * 删除文档
     */
    @DeleteMapping("/{kbId}/documents/{docId}")
    public ApiResponse<Void> deleteDocument(@PathVariable Long kbId, @PathVariable Long docId) {
        permissionService.requireWrite(kbId);
        knowledgeBaseService.deleteDocument(docId);
        return ApiResponse.ok(null);
    }

    /**
     * 下载原始文件
     */
    @GetMapping("/{kbId}/documents/{docId}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long kbId, @PathVariable Long docId) {
        permissionService.requireRead(kbId);
        KbDocument kbDocument = kbDocumentRepository.findById(docId);
        if (Objects.isNull(kbDocument)) {
            throw new RuntimeException("文档不存在");
        }
        byte[] content = minioStorageService.download(kbDocument.getMinioPath());
        String encodedName = URLEncoder.encode(kbDocument.getFileName(), StandardCharsets.UTF_8)
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
    public ApiResponse<String> reindex(@PathVariable Long kbId, @PathVariable Long docId) {
        permissionService.requireWrite(kbId);
        knowledgeBaseService.reindex(docId);
        return ApiResponse.ok("重建索引任务已提交，请通过 /status 接口查询进度");
    }
}