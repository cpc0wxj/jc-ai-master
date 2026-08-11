package com.jichi.ragkb.service;

import com.jichi.ragkb.entity.KbDocument;
import com.jichi.ragkb.repository.KbDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;

/**
 * 文档更新的事务边界 Service
 * 单独拆出来是因为 Spring @Transactional 基于 AOP 代理，同一 Bean 内方法互调会绕过代理导致事务失效
 * 把事务方法放到独立 Bean，让编排方法跨 Bean 调用——代理生效，事务才真启动并提交
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentTxService {
    private final KbDocumentRepository documentRepository;

    private final MinioStorageService minioService;

    /**
     * 在一个事务里完成：上传新文件 + 更新文档记录
     * 返回旧文件路径，供事务提交后异步删除
     */
    @Transactional
    public String updateDocumentRecord(Long docId, MultipartFile newFile) {
        KbDocument kbDocument = documentRepository.findById(docId);
        if (Objects.isNull(kbDocument)) {
            throw new RuntimeException("文档不存在：" + docId);
        }
        if (Boolean.TRUE.equals(kbDocument.getIsDeleted())) {
            throw new RuntimeException("文档已删除，无法替换：" + docId);
        }

        String oldMinioPath = kbDocument.getMinioPath();
        String newMinioPath = minioService.upload(kbDocument.getKbId(), newFile);

        kbDocument.setFileName(newFile.getOriginalFilename())
                .setFileSize(newFile.getSize())
                .setMinioPath(newMinioPath)
                .setVersion(kbDocument.getVersion() + 1)
                .setStatus(KbDocument.DocumentStatus.PENDING)
                .setErrorMsg(null)
                .setChunkCount(null)
                .setTokenCount(null)
                .setIndexedAt(null);
        documentRepository.updateById(kbDocument);

        log.info("DocumentTxService.updateDocumentRecord docId={},newVersion={},newFile={}", docId, kbDocument.getVersion(), newFile.getOriginalFilename());
        return oldMinioPath;
    }

    /**
     * 强制重建索引（文件未变，只是想用新策略重新切分/向量化）
     */
    @Transactional
    public void forceReindex(Long docId) {
        KbDocument doc = documentRepository.findById(docId);
        if (Objects.isNull(doc)) {
            throw new RuntimeException("文档不存在：" + docId);
        }
        if (Boolean.TRUE.equals(doc.getIsDeleted())) {
            throw new RuntimeException("文档已删除，无法重建：" + docId);
        }

        doc.setVersion(doc.getVersion() + 1)
                .setStatus(KbDocument.DocumentStatus.PENDING)
                .setErrorMsg(null)
                .setChunkCount(null)
                .setTokenCount(null)
                .setIndexedAt(null);
        documentRepository.updateById(doc);

        log.info("DocumentTxService.forceReindex docId={},newVersion={}", docId, doc.getVersion());
    }
}