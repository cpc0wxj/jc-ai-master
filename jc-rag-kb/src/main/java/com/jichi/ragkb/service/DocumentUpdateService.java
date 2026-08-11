package com.jichi.ragkb.service;

import com.jichi.ragkb.entity.KbDocument;
import com.jichi.ragkb.repository.KbDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;

/**
 * 文档更新的编排 Service
 * 自身不放任何事务——所有事务边界都在 DocumentTxService 里
 *
 * 编排顺序：
 * 1. 跨 Bean 调用 documentTxService 的事务方法——事务在那一刻完整启动并提交
 * 2. 事务提交后再触发异步索引——保证异步线程从 DB 能读到最新数据
 * 3. 最后清理旧的 MinIO 文件——异步任务读的是新 minioPath，与旧文件无关
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentUpdateService {
    private final DocumentTxService documentTxService;
    private final KbDocumentRepository kbDocumentRepository;
    private final MinioStorageService minioStorageService;
    private final IndexService indexService;

    /**
     * 替换文档内容，保持文档 ID 不变
     */
    public KbDocument replaceDocument(Long docId, MultipartFile newFile) {
        String oldMinioPath = documentTxService.updateDocumentRecord(docId, newFile);
        indexService.submitIndexTask(docId);
        minioStorageService.delete(oldMinioPath);
        KbDocument kbDocument = kbDocumentRepository.findById(docId);
        if (Objects.isNull(kbDocument)) {
            throw new RuntimeException("文档不存在：" + docId);
        }
        return kbDocument;
    }

    /**
     * 强制重建索引（文件字节未变、但解析或分块策略变了等场景）
     */
    public void forceReindexAndSubmit(Long docId) {
        documentTxService.forceReindex(docId);
        indexService.submitIndexTask(docId);
        log.info("DocumentUpdateService.forceReindexAndSubmit docId={}", docId);
    }
}