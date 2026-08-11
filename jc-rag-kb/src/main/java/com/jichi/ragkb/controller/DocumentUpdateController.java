package com.jichi.ragkb.controller;

import com.jichi.ragkb.dto.ApiResponse;
import com.jichi.ragkb.entity.KbDocument;
import com.jichi.ragkb.service.DocumentUpdateService;
import com.jichi.ragkb.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档更新接口
 * 提供文档内容替换和强制重建索引功能
 */
@RestController
@RequiredArgsConstructor
public class DocumentUpdateController {

    private final PermissionService permissionService;
    private final DocumentUpdateService documentUpdateService;

    /**
     * 替换文档内容（文档 ID 不变），触发新版本索引
     */
    @PutMapping("/api/v1/kb/{kbId}/documents/{docId}/content")
    public ApiResponse<KbDocument> replaceContent(
            @PathVariable Long kbId,
            @PathVariable Long docId,
            @RequestParam("file") MultipartFile file) {
        permissionService.requireWrite(kbId);
        return ApiResponse.ok(documentUpdateService.replaceDocument(docId, file));
    }

    /**
     * 强制重建索引（文件字节未变、但解析或分块策略变了等场景）
     * 与「上传后首次索引」不同，这里是显式触发
     */
    @PostMapping("/api/v1/kb/{kbId}/documents/{docId}/reindex-force")
    public ApiResponse<Void> forceReindex(
            @PathVariable Long kbId,
            @PathVariable Long docId) {
        permissionService.requireWrite(kbId);
        documentUpdateService.forceReindexAndSubmit(docId);
        return ApiResponse.ok(null);
    }
}
