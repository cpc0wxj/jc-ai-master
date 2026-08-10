package com.jichi.ragkb.service;

import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.collection.ListUtil;
import com.google.common.collect.Lists;
import com.jichi.ragkb.dto.ChunkResult;
import com.jichi.ragkb.dto.ParseResult;
import com.jichi.ragkb.entity.DocChunk;
import com.jichi.ragkb.entity.IndexTask;
import com.jichi.ragkb.entity.KbDocument;
import com.jichi.ragkb.repository.DocChunkRepository;
import com.jichi.ragkb.repository.IndexTaskRepository;
import com.jichi.ragkb.repository.KbDocumentRepository;
import com.jichi.ragkb.service.manager.parse.DocumentParseManager;
import com.jichi.ragkb.service.manager.splitter.ChunkSplitManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexService {
    private final KbDocumentRepository documentRepository;
    private final DocChunkRepository chunkRepository;
    private final IndexTaskRepository taskRepository;
    private final DocumentParseManager loaderService;
    private final ChunkSplitManager chunkSplitManager;
    private final EmbeddingService embeddingService;
    private final MinioStorageService minioStorageService;
    private final IndexTaskLauncher indexTaskLauncher;

    /**
     * 提交索引任务（支持直接传入文本，测试时跳过 MinIO）。
     */
    public void submitIndexTask(Long docId, String textContent) {
        IndexTask indexTask = new IndexTask()
                .setDocId(docId)
                .setTaskType("INDEX");
        taskRepository.save(indexTask);

        // 通过 taskLauncher 触发异步（不能直接 this.executeWithText，会绕过代理）
        indexTaskLauncher.launchWithText(indexTask.getId(), docId, textContent);
    }

    /**
     * 提交索引任务（生产模式，从 MinIO 读取文件）。
     */
    public void submitIndexTask(Long docId) {
        IndexTask indexTask = new IndexTask()
                .setDocId(docId)
                .setTaskType("INDEX");
        taskRepository.save(indexTask);

        indexTaskLauncher.launchFromMinio(indexTask.getId(), docId);
    }

    /**
     * 执行索引（直接使用文本内容，由 IndexTaskLauncher 异步调用）。
     */
    public void executeWithText(Long taskId, Long docId, String textContent) {
        KbDocument kbDocument = documentRepository.findById(docId);
        if (Objects.isNull(kbDocument)) {
            throw new RuntimeException("文档不存在: " + docId);
        }

        ParseResult.PageContent pageContent = new ParseResult.PageContent()
                .setPageNum(1)
                .setText(textContent);
        ParseResult parseResult = new ParseResult()
                .setSuccess(true)
                .setPageContentList(List.of(pageContent))
                .setTotalPageNum(1);
        doIndex(taskId, docId, kbDocument, parseResult);
    }

    /**
     * 从 MinIO 读取文件并执行索引（由 IndexTaskLauncher 异步调用）。
     */
    public void executeFromMinio(Long taskId, Long docId) {
        KbDocument kbDocument = documentRepository.findById(docId);
        if (Objects.isNull(kbDocument)) {
            throw new RuntimeException("文档不存在: " + docId);
        }

        try {
            byte[] fileBytes = minioStorageService.download(kbDocument.getMinioPath());
            ParseResult parseResult = loaderService.load(kbDocument.getFileName(), new ByteArrayInputStream(fileBytes));
            doIndex(taskId, docId, kbDocument, parseResult);
        } catch (Exception e) {
            markFailed(taskId, docId, "从MinIO读取文件失败：" + e.getMessage());
        }
    }

    /**
     * 核心索引逻辑：解析 → 分块 → Embedding → 写库。
     * 设计思路：整个管道是"先算后删再写"的顺序，而不是"先删再算再写"。
     * 这样设计是为了保证可用性——如果 Embedding 阶段调 API 失败了，
     * 旧版本的分块还在，用户查询不受影响。
     * 只有当新向量算完、确定能写入了，才去删旧数据。
     */
    private void doIndex(Long taskId, Long docId, KbDocument kbDocument, ParseResult parseResult) {
        IndexTask indexTask = new IndexTask()
                .setId(taskId)
                .setStatus(IndexTask.TaskStatus.RUNNING)
                .setStartedAt(LocalDateTime.now());
        taskRepository.updateById(indexTask);
        documentRepository.updateById(new KbDocument().setId(docId).setStatus(KbDocument.DocumentStatus.PROCESSING));

        try {
            if (!Objects.equals(parseResult.getSuccess(), Boolean.TRUE)) {
                throw new RuntimeException("文档解析失败：" + parseResult.getErrorMsg());
            }

            // 分块
            List<ChunkResult> chunkResultList = chunkSplitManager.chunk(parseResult);
            if (CollectionUtils.isEmpty(chunkResultList)) {
                throw new RuntimeException("分块结果为空，文档可能无有效文本内容");
            }
            log.info("IndexService.doIndex docId={},chunkResultListSize={}", docId, chunkResultList.size());

            // 批量 Embedding
            List<String> textList = CollStreamUtil.toList(chunkResultList, ChunkResult::getContent);
            List<float[]> embeddingList = embeddingService.embedBatch(textList);

            // 删除旧版本分块（放在 Embedding 成功之后，保证有新数据才删旧数据）
            chunkRepository.deleteByDocIdAndDocVersionLessThan(docId, kbDocument.getVersion());

            // 批量写入数据库
            List<DocChunk> docChunkList = Lists.newArrayList();
            int totalTokens = 0;
            for (int i = 0; i < chunkResultList.size(); i++) {
                ChunkResult chunkResult = chunkResultList.get(i);
                DocChunk docChunk = new DocChunk()
                        .setDocId(docId)
                        .setKbId(kbDocument.getKbId())
                        .setChunkIndex(chunkResult.getChunkIndex())
                        .setContent(chunkResult.getContent())
                        .setEmbedding(embeddingList.get(i))
                        .setPageNum(chunkResult.getPageNum())
                        .setSectionTitle(chunkResult.getSectionTitle())
                        .setTokenCount(chunkResult.getEstimatedTokens())
                        .setDocVersion(kbDocument.getVersion());
                docChunkList.add(docChunk);
                totalTokens += chunkResult.getEstimatedTokens();
            }

            // 分批写入，每批 50 条
            for (List<DocChunk> batchList : ListUtil.split(docChunkList, 50)) {
                chunkRepository.saveAll(batchList);
            }

            // 更新文档状态
            kbDocument.setStatus(KbDocument.DocumentStatus.DONE)
                    .setChunkCount(chunkResultList.size())
                    .setTokenCount(totalTokens)
                    .setIndexedAt(LocalDateTime.now());
            documentRepository.updateById(kbDocument);

            indexTask = new IndexTask()
                    .setId(taskId)
                    .setStatus(IndexTask.TaskStatus.DONE)
                    .setFinishedAt(LocalDateTime.now());
            taskRepository.updateById(indexTask);

            log.info("IndexService.doIndex docId={},chunkResultListSize={},totalTokens={}", docId, chunkResultList.size(), totalTokens);
        } catch (Exception e) {
            log.error("IndexService.doIndex docId={},error={}", docId, e.getMessage(), e);
            markFailed(taskId, docId, e.getMessage());
            retryIfPossible(taskId, docId);
        }
    }

    private void markFailed(Long taskId, Long docId, String errorMsg) {
        IndexTask indexTask = taskRepository.findById(taskId);
        if (Objects.isNull(indexTask)) {
            throw new RuntimeException("任务不存在: " + taskId);
        }

        indexTask.setStatus(IndexTask.TaskStatus.FAILED)
                .setErrorMsg(errorMsg)
                .setFinishedAt(LocalDateTime.now());
        taskRepository.updateById(indexTask);

        KbDocument kbDocument = new KbDocument()
                .setId(docId)
                .setStatus(KbDocument.DocumentStatus.FAILED)
                .setErrorMsg(errorMsg);
        documentRepository.updateById(kbDocument);
    }

    private void retryIfPossible(Long taskId, Long docId) {
        IndexTask indexTask = taskRepository.findById(taskId);
        if (Objects.isNull(indexTask)) {
            throw new RuntimeException("任务不存在: " + taskId);
        }

        if (indexTask.getRetryCount() < indexTask.getMaxRetry() && Objects.equals(indexTask.getStatus(), IndexTask.TaskStatus.FAILED)) {
            indexTask.setRetryCount(indexTask.getRetryCount() + 1)
                    .setStatus(IndexTask.TaskStatus.PENDING);
            taskRepository.updateById(indexTask);
            log.info("IndexService.retryIfPossible taskId={},retryCount={}", taskId, indexTask.getRetryCount());
            // 延迟重试（指数退避：1s, 2s, 4s）
            scheduleRetry(taskId, docId, indexTask.getRetryCount());
        }
    }

    /**
     * 延迟重试（指数退避）。
     * 这里直接用 Thread.sleep 而不是 ScheduledExecutorService，
     * 因为当前已经在 indexTaskExecutor 的异步线程里了，sleep 不会阻塞主线程。
     * 用 ScheduledExecutorService 反而要引入额外的线程池管理，过度设计了。
     */
    protected void scheduleRetry(Long taskId, Long docId, int retryCount) {
        try {
            long delay = (long) Math.pow(2, retryCount - 1) * 1000;
            Thread.sleep(delay);
            executeFromMinio(taskId, docId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}