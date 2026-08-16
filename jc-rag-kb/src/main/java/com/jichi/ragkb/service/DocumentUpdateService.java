package com.jichi.ragkb.service;

import com.jichi.ragkb.entity.KbDocument;
import com.jichi.ragkb.repository.KbDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;

/**
 * 文档更新的编排 Service
 * 事务方法与编排方法同处一类——Spring @Transactional 基于 AOP 代理，同类内 this 互调会绕过代理导致事务失效
 * 因此编排方法通过 AopContext.currentProxy() 显式走代理调用事务方法，保证事务真正启动并提交
 *
 * 编排顺序：
 * 1. 走代理调用本类事务方法——事务在那一刻完整启动并提交
 * 2. 事务提交后再触发异步索引——保证异步线程从 DB 能读到最新数据
 * 3. 最后清理旧的 MinIO 文件——异步任务读的是新 minioPath，与旧文件无关
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentUpdateService {
    private final KbDocumentRepository kbDocumentRepository;
    private final MinioStorageService minioStorageService;
    private final IndexService indexService;

    /**
     * 替换文档内容，保持文档 ID 不变
     */
    public KbDocument replaceDocument(Long docId, MultipartFile newFile) {
        String oldMinioPath = ((DocumentUpdateService) AopContext.currentProxy()).updateDocumentRecord(docId, newFile);
        indexService.submitIndexTask(docId);
        minioStorageService.delete(oldMinioPath);
        return kbDocumentRepository.findById(docId);
    }

    /**
     * 强制重建索引（文件字节未变、但解析或分块策略变了等场景）
     */
    public void forceReindexAndSubmit(Long docId) {
        // 通过 AopContext.currentProxy() 获取代理对象调用, 确保 @Transactional 生效
        ((DocumentUpdateService) AopContext.currentProxy()).forceReindex(docId);
        indexService.submitIndexTask(docId);
        log.info("DocumentUpdateService.forceReindexAndSubmit docId={}", docId);
    }

    /**
     * 在一个事务里完成：上传新文件 + 更新文档记录
     * 返回旧文件路径，供事务提交后异步删除
     */
    @Transactional
    public String updateDocumentRecord(Long docId, MultipartFile newFile) {
        KbDocument kbDocument = kbDocumentRepository.findById(docId);
        if (Objects.isNull(kbDocument)) {
            throw new RuntimeException("文档不存在：" + docId);
        }
        if (Objects.equals(kbDocument.getIsDeleted(), Boolean.TRUE)) {
            throw new RuntimeException("文档已删除，无法替换：" + docId);
        }

        String oldMinioPath = kbDocument.getMinioPath();
        String newMinioPath = minioStorageService.upload(kbDocument.getKbId(), newFile);

        kbDocument.setFileName(newFile.getOriginalFilename())
                .setFileSize(newFile.getSize())
                .setMinioPath(newMinioPath)
                .setVersion(kbDocument.getVersion() + 1)
                .setStatus(KbDocument.DocumentStatus.PENDING)
                .setErrorMsg(null)
                .setChunkCount(null)
                .setTokenCount(null)
                .setIndexedAt(null);
        kbDocumentRepository.updateById(kbDocument);

        log.info("DocumentUpdateService.updateDocumentRecord docId={},newVersion={},newFile={}", docId, kbDocument.getVersion(), newFile.getOriginalFilename());
        return oldMinioPath;
    }

    /**
     * 强制重建索引（文件未变，只是想用新策略重新切分/向量化）
     */
    @Transactional
    public void forceReindex(Long docId) {
        KbDocument kbDocument = kbDocumentRepository.findById(docId);
        if (Objects.isNull(kbDocument)) {
            throw new RuntimeException("文档不存在：" + docId);
        }
        if (Boolean.TRUE.equals(kbDocument.getIsDeleted())) {
            throw new RuntimeException("文档已删除，无法重建：" + docId);
        }

        kbDocument.setVersion(kbDocument.getVersion() + 1)
                .setStatus(KbDocument.DocumentStatus.PENDING)
                .setErrorMsg(null)
                .setChunkCount(null)
                .setTokenCount(null)
                .setIndexedAt(null);
        kbDocumentRepository.updateById(kbDocument);

        log.info("DocumentUpdateService.forceReindex docId={},newVersion={}", docId, kbDocument.getVersion());
    }
}
