package com.jichi.ragkb.service;

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
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final IndexTaskLauncher taskLauncher;   // 后面会讲为什么要抽出这个类

    /**
     * 提交索引任务（支持直接传入文本，测试时跳过 MinIO）。
     */
    public void submitIndexTask(Long docId, String textContent) {
        IndexTask task = new IndexTask();
        task.setDocId(docId);
        task.setTaskType("INDEX");
        taskRepository.save(task);

        // 通过 taskLauncher 触发异步（不能直接 this.executeWithText，会绕过代理）
        taskLauncher.launchWithText(task.getId(), docId, textContent);
    }

    /**
     * 提交索引任务（生产模式，从 MinIO 读取文件）。
     */
    public void submitIndexTask(Long docId) {
        IndexTask task = new IndexTask();
        task.setDocId(docId);
        task.setTaskType("INDEX");
        taskRepository.save(task);

        taskLauncher.launchFromMinio(task.getId(), docId);
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
     * 执行索引（直接使用文本内容，由 IndexTaskLauncher 异步调用）。
     */
    public void executeWithText(Long taskId, Long docId, String textContent) {
        KbDocument kbDocument = documentRepository.findById(docId);
        if (Objects.isNull(kbDocument)) {
            throw new RuntimeException("文档不存在: " + docId);
        }

        ParseResult parseResult = new ParseResult()
                .setSuccess(true)
                .setPageContentList(List.of(new ParseResult.PageContent()
                        .setPageNum(1)
                        .setText(textContent)))
                .setTotalPageNum(1);
        doIndex(taskId, docId, kbDocument, parseResult);
    }

    /**
     * 核心索引逻辑：解析 → 分块 → Embedding → 写库。
     * 设计思路：整个管道是"先算后删再写"的顺序，而不是"先删再算再写"。
     * 这样设计是为了保证可用性——如果 Embedding 阶段调 API 失败了，
     * 旧版本的分块还在，用户查询不受影响。
     * 只有当新向量算完、确定能写入了，才去删旧数据。
     */
    private void doIndex(Long taskId, Long docId, KbDocument kbDocument, ParseResult parseResult) {
        updateTaskStatus(taskId, IndexTask.TaskStatus.RUNNING);
        updateDocStatus(docId, KbDocument.DocumentStatus.PROCESSING);

        try {
            if (!parseResult.getSuccess()) {
                throw new RuntimeException("文档解析失败：" + parseResult.getErrorMsg());
            }

            // 第一步：分块
            List<ChunkResult> chunks = chunkSplitManager.chunk(parseResult);
            if (chunks.isEmpty()) {
                throw new RuntimeException("分块结果为空，文档可能无有效文本内容");
            }
            log.info("[IndexService] docId={}，分块完成，共{}块", docId, chunks.size());

            // 第二步：批量 Embedding
            List<String> texts = chunks.stream().map(ChunkResult::getContent).toList();
            List<float[]> embeddings = embeddingService.embedBatch(texts);

            // 第三步：删除旧版本分块（放在 Embedding 成功之后，保证有新数据才删旧数据）
            chunkRepository.deleteByDocIdAndDocVersionLessThan(docId, kbDocument.getVersion());

            // 第四步：批量写入数据库
            List<DocChunk> docChunks = new ArrayList<>();
            int totalTokens = 0;
            for (int i = 0; i < chunks.size(); i++) {
                ChunkResult chunk = chunks.get(i);
                DocChunk docChunk = new DocChunk();
                docChunk.setDocId(docId);
                docChunk.setKbId(kbDocument.getKbId());
                docChunk.setChunkIndex(chunk.getChunkIndex());
                docChunk.setContent(chunk.getContent());
                docChunk.setEmbedding(embeddings.get(i));
                docChunk.setPageNum(chunk.getPageNum());
                docChunk.setSectionTitle(chunk.getSectionTitle());
                docChunk.setTokenCount(chunk.getEstimatedTokens());
                docChunk.setDocVersion(kbDocument.getVersion());
                docChunks.add(docChunk);
                totalTokens += chunk.getEstimatedTokens();
            }

            batchInsertChunks(docChunks);

            // 第五步：更新文档状态
            kbDocument.setStatus(KbDocument.DocumentStatus.DONE);
            kbDocument.setChunkCount(chunks.size());
            kbDocument.setTokenCount(totalTokens);
            kbDocument.setIndexedAt(LocalDateTime.now());
            documentRepository.updateById(kbDocument);

            updateTaskStatus(taskId, IndexTask.TaskStatus.DONE);

            log.info("[IndexService] 索引完成：docId={}，chunks={}，tokens={}", docId, chunks.size(), totalTokens);
        } catch (Exception e) {
            log.error("[IndexService] 索引失败：docId={}，error={}", docId, e.getMessage(), e);
            markFailed(taskId, docId, e.getMessage());
            retryIfPossible(taskId, docId);
        }
    }

    /**
     * 分批写入，每批 50 条。
     * 为什么不直接 saveAll 一把梭？因为一份大文档可能有几百个 chunk，
     * 单次 INSERT 几百行对数据库的压力很大（长事务 + 大量 WAL 日志），
     * 分批写可以减少单次事务大小，也方便观察写入进度。
     */
    private void batchInsertChunks(List<DocChunk> chunks) {
        int batchSize = 50;
        for (int i = 0; i < chunks.size(); i += batchSize) {
            List<DocChunk> batch = chunks.subList(i, Math.min(i + batchSize, chunks.size()));
            chunkRepository.saveAll(batch);
            log.debug("[IndexService] 写入批次 {}/{}",
                    i / batchSize + 1, (chunks.size() + batchSize - 1) / batchSize);
        }
    }

    private void markFailed(Long taskId, Long docId, String errorMsg) {
        IndexTask indexTask = taskRepository.findById(taskId);
        if (Objects.isNull(indexTask)) {
            throw new RuntimeException("任务不存在: " + taskId);
        }

        indexTask.setStatus(IndexTask.TaskStatus.FAILED);
        indexTask.setErrorMsg(errorMsg);
        indexTask.setFinishedAt(LocalDateTime.now());
        taskRepository.updateById(indexTask);

        KbDocument kbDocument = documentRepository.findById(docId);
        if (Objects.nonNull(kbDocument)) {
            kbDocument.setStatus(KbDocument.DocumentStatus.FAILED);
            kbDocument.setErrorMsg(errorMsg);
            documentRepository.updateById(kbDocument);
        }
    }

    private void retryIfPossible(Long taskId, Long docId) {
        IndexTask indexTask = taskRepository.findById(taskId);
        if (Objects.isNull(indexTask)) {
            throw new RuntimeException("任务不存在: " + taskId);
        }

        if (indexTask.canRetry()) {
            indexTask.setRetryCount(indexTask.getRetryCount() + 1);
            indexTask.setStatus(IndexTask.TaskStatus.PENDING);
            taskRepository.updateById(indexTask);
            log.info("[IndexService] 任务将重试：taskId={}，retryCount={}", taskId, indexTask.getRetryCount());
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

    private void updateTaskStatus(Long taskId, IndexTask.TaskStatus status) {
        IndexTask indexTask = taskRepository.findById(taskId);
        if (Objects.isNull(indexTask)) {
            return;
        }

        indexTask.setStatus(status);
        if (status == IndexTask.TaskStatus.RUNNING) {
            indexTask.setStartedAt(LocalDateTime.now());
        }
        if (status == IndexTask.TaskStatus.DONE) {
            indexTask.setFinishedAt(LocalDateTime.now());
        }
        taskRepository.updateById(indexTask);
    }

    private void updateDocStatus(Long docId, KbDocument.DocumentStatus status) {
        KbDocument kbDocument = documentRepository.findById(docId);
        if (Objects.isNull(kbDocument)) {
            return;
        }

        kbDocument.setStatus(status);
        documentRepository.updateById(kbDocument);
    }
}