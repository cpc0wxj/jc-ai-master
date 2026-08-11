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
import com.jichi.ragkb.security.UserContext;
import com.jichi.ragkb.service.manager.parse.DocumentParseManager;
import com.jichi.ragkb.service.manager.splitter.ChunkSplitManager;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexService {
    private final KbDocumentRepository kbDocumentRepository;
    private final DocChunkRepository docChunkRepository;
    private final IndexTaskRepository indexTaskRepository;
    private final DocumentParseManager documentParseManager;
    private final ChunkSplitManager chunkSplitManager;
    private final EmbeddingService embeddingService;
    private final MinioStorageService minioStorageService;
    /**
     * 索引线程池——所有索引任务（初始提交 + 延迟重试）都通过此线程池异步执行。
     */
    private final Executor indexTaskExecutor;

    /**
     * 重试调度器：单线程、daemon——专用于"延迟到时间后把任务重新投递到 indexTaskExecutor"。
     * 不直接 Thread.sleep 在业务线程里——避免占用 indexTaskExecutor 的 slot，
     * 高并发失败时 sleep 会让索引线程池整体阻塞。
     */
    private final ScheduledExecutorService retryScheduler = Executors.newSingleThreadScheduledExecutor(
            runnable -> {
                Thread thread = new Thread(runnable, "index-retry-scheduler");
                thread.setDaemon(true);
                return thread;
            });

    @PreDestroy
    void shutdownRetryScheduler() {
        retryScheduler.shutdown();
    }

    /**
     * 提交索引任务（支持直接传入文本，测试时跳过 MinIO）。
     */
    public void submitIndexTask(Long docId, String textContent) {
        IndexTask indexTask = new IndexTask()
                .setDocId(docId)
                .setTaskType("INDEX_FROM_TEXT");   // ★ 区分入口——scheduleRetry 据此跳过文本任务
        indexTaskRepository.save(indexTask);

        // 捕获当前用户上下文，传递给异步线程（ThreadLocal 不能跨线程）
        Runnable runnable = () -> executeWithText(indexTask.getId(), docId, textContent, UserContext.getUserId(), UserContext.getDepartmentId(), UserContext.getRole());
        indexTaskExecutor.execute(runnable);
    }

    /**
     * 提交索引任务（生产模式，从 MinIO 读取文件）。
     */
    public void submitIndexTask(Long docId) {
        IndexTask indexTask = new IndexTask()
                .setDocId(docId)
                .setTaskType("INDEX_FROM_MINIO");  // ★ 区分入口——scheduleRetry 才能正确地走 MinIO 路径
        indexTaskRepository.save(indexTask);

        Runnable runnable = () -> executeFromMinio(indexTask.getId(), docId, UserContext.getUserId(), UserContext.getDepartmentId(), UserContext.getRole());
        indexTaskExecutor.execute(runnable);
    }

    /**
     * 执行索引（直接使用文本内容，由 submitIndexTask 提交到 indexTaskExecutor 异步执行）。
     * 注意：文本任务失败后不能自动重试——文本只在内存里，重启就丢，scheduleRetry 会跳过这种 task。
     */
    public void executeWithText(Long taskId, Long docId, String textContent, Long userId, String departmentId, String role) {
        // 在异步线程中恢复用户上下文
        UserContext.set(userId, departmentId, role);
        try {
            KbDocument kbDocument;
            try {
                kbDocument = kbDocumentRepository.findById(docId);
                if (Objects.isNull(kbDocument)) {
                    throw new RuntimeException("文档不存在：docId=" + docId);
                }
            } catch (Exception e) {
                markFailed(taskId, docId, e.getMessage());
                return;
            }

            ParseResult.PageContent pageContent = new ParseResult.PageContent()
                    .setPageNum(1)
                    .setText(textContent);
            ParseResult parseResult = new ParseResult()
                    .setSuccess(true)
                    .setPageContentList(List.of(pageContent))
                    .setTotalPageNum(1);
            doIndex(taskId, docId, kbDocument, parseResult);
        } finally {
            UserContext.clear();
        }
    }

    /**
     * 从 MinIO 读取文件并执行索引（由 submitIndexTask / scheduleRetry 提交到 indexTaskExecutor 异步执行）
     * 分阶段 try-catch 的理由：
     * → 文档不存在：是数据被并发删除的脏请求，重试也没用，直接 markFailed 终止
     * → MinIO 下载失败：通常是网络抖动 / 对象不存在，值得重试 → retryIfPossible
     * → 解析/索引失败：可能是文件本身格式坏、也可能是依赖服务抖动，先重试，重试上限到了再放弃
     */
    public void executeFromMinio(Long taskId, Long docId, Long userId, String departmentId, String role) {
        // 在异步线程中恢复用户上下文
        UserContext.set(userId, departmentId, role);
        try {
            KbDocument kbDocument;
            // 取文档元数据——失败说明 docId 已被删，不重试
            try {
                kbDocument = kbDocumentRepository.findById(docId);
                if (Objects.isNull(kbDocument)) {
                    throw new RuntimeException("文档不存在：docId=" + docId);
                }
            } catch (Exception e) {
                markFailed(taskId, docId, e.getMessage());
                return;   // ★ 不进入 retryIfPossible——重试也找不到这条记录
            }

            // 从 MinIO 下载文件——网络/IO 失败，值得重试
            byte[] fileBytes;
            try {
                fileBytes = minioStorageService.download(kbDocument.getMinioPath());
            } catch (Exception e) {
                markFailed(taskId, docId, "从 MinIO 读取文件失败：" + e.getMessage());
                retryIfPossible(taskId, docId);
                return;
            }

            // 解析 + 索引——失败可能是文件格式坏，也可能是 Embedding/DB 抖动，先重试
            try {
                ParseResult parseResult = documentParseManager.load(kbDocument.getFileName(), new ByteArrayInputStream(fileBytes));
                doIndex(taskId, docId, kbDocument, parseResult);
                // doIndex 内部自带 try-catch，失败时已 markFailed + retry，这里 catch 兜底极端情况
            } catch (Exception e) {
                markFailed(taskId, docId, "文档解析或索引失败：" + e.getMessage());
                retryIfPossible(taskId, docId);
            }
        } finally {
            UserContext.clear();
        }
    }

    /**
     * 核心索引逻辑：解析 → 分块 → Embedding → 写新版本 → 删旧版本 → 更新状态。
     * <p>
     * 设计思路（关键）：新版本写入完成后，才删除旧版本——真正的"先写后删"。
     * 每次 doIndex 都把 doc.version + 1，新写入的 chunk 用新版本号；
     * 只有 batchInsertChunks 全部完成，才会 deleteByDocIdAndDocVersionLessThan(newVersion)。
     * 即使新数据写入中途 JVM 挂掉，旧版本数据还在，用户查询不受影响，
     * 下一次重试又会再把 version+1 重新走一遍，幂等。
     */
    private void doIndex(Long taskId, Long docId, KbDocument kbDocument, ParseResult parseResult) {
        IndexTask indexTask = new IndexTask()
                .setId(taskId)
                .setStatus(IndexTask.TaskStatus.RUNNING)
                .setStartedAt(LocalDateTime.now());
        indexTaskRepository.updateById(indexTask);
        kbDocumentRepository.updateById(new KbDocument().setId(docId).setStatus(KbDocument.DocumentStatus.PROCESSING));

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

            // 递增版本号——下面写入用新版本号；旧 chunk 保持旧版本号不动
            int newVersion = (kbDocument.getVersion() == null ? 1 : kbDocument.getVersion() + 1);
            kbDocument.setVersion(newVersion);
            kbDocument.setStatus(KbDocument.DocumentStatus.PROCESSING);  // ★ 保持 PROCESSING，不被覆盖
            kbDocumentRepository.updateById(kbDocument);

            // 批量写入新版本数据
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
                        .setDocVersion(newVersion);   // ★ 用新版本号
                docChunkList.add(docChunk);
                totalTokens += chunkResult.getEstimatedTokens();
            }

            for (List<DocChunk> batchList : ListUtil.split(docChunkList, 50)) {
                docChunkRepository.saveBatch(batchList);
                log.info("IndexService.batchInsertChunks batchSize={}", batchList.size());
            }

            // 新数据写入完成后，才删除旧版本——真正的"先写后删"
            //   即使这一步挂了，旧版本 chunk 也只是没删干净，下次重建会再清，
            //   不会出现"旧的没了、新的没全"的窗口。
            docChunkRepository.deleteByDocIdAndDocVersionLessThan(docId, newVersion);

            // 第六步：更新文档状态
            kbDocument.setStatus(KbDocument.DocumentStatus.DONE)
                    .setChunkCount(chunkResultList.size())
                    .setTokenCount(totalTokens)
                    .setIndexedAt(LocalDateTime.now());
            kbDocumentRepository.updateById(kbDocument);

            indexTask = new IndexTask()
                    .setId(taskId)
                    .setStatus(IndexTask.TaskStatus.DONE)
                    .setFinishedAt(LocalDateTime.now());
            indexTaskRepository.updateById(indexTask);

            log.info("IndexService.doIndex docId={},version={},chunkResultListSize={},totalTokens={}", docId, newVersion, chunkResultList.size(), totalTokens);
        } catch (Exception e) {
            log.error("IndexService.doIndex docId={},error={}", docId, e.getMessage(), e);
            markFailed(taskId, docId, e.getMessage());
            retryIfPossible(taskId, docId);
        }
    }

    private void markFailed(Long taskId, Long docId, String errorMsg) {
        IndexTask indexTask = indexTaskRepository.findById(taskId);
        if (Objects.isNull(indexTask)) {
            throw new RuntimeException("任务不存在: " + taskId);
        }

        indexTask.setStatus(IndexTask.TaskStatus.FAILED)
                .setErrorMsg(errorMsg)
                .setFinishedAt(LocalDateTime.now());
        indexTaskRepository.updateById(indexTask);

        kbDocumentRepository.updateById(new KbDocument()
                .setId(docId)
                .setStatus(KbDocument.DocumentStatus.FAILED)
                .setErrorMsg(errorMsg));
    }

    private void retryIfPossible(Long taskId, Long docId) {
        IndexTask indexTask = indexTaskRepository.findById(taskId);
        if (Objects.isNull(indexTask)) {
            throw new RuntimeException("任务不存在: " + taskId);
        }

        if (indexTask.getRetryCount() < indexTask.getMaxRetry() && Objects.equals(indexTask.getStatus(), IndexTask.TaskStatus.FAILED)) {
            indexTask.setRetryCount(indexTask.getRetryCount() + 1)
                    .setStatus(IndexTask.TaskStatus.PENDING);
            indexTaskRepository.updateById(indexTask);
            log.info("IndexService.retryIfPossible taskId={},retryCount={}", taskId, indexTask.getRetryCount());
            // 延迟重试（指数退避：1s, 2s, 4s）
            scheduleRetry(taskId, docId, indexTask.getRetryCount());
        }
    }

    /**
     * 延迟重试——指数退避（1s → 2s → 4s …）。
     * 关键设计：
     * 1. 用独立的 retryScheduler 延迟、不在 indexTaskExecutor 线程里 Thread.sleep——
     * 否则多个失败任务并发 sleep 会把索引线程池整体阻塞。
     * 2. 时间到了直接通过 indexTaskExecutor 线程池提交任务——
     * UserContext 在新线程里重新 set。
     * 3. 根据 task.taskType 正确选择重投入口——
     * MinIO 任务走 executeFromMinio，文本任务（文本只在内存里）直接放弃自动重试。
     */
    protected void scheduleRetry(Long taskId, Long docId, int retryCount) {
        IndexTask indexTask = indexTaskRepository.findById(taskId);
        if (Objects.isNull(indexTask)) {
            log.warn("IndexService.scheduleRetry indexTask未找到 taskId={}", taskId);
            return;
        }

        // 若为文本任务
        if (Objects.equals(indexTask.getTaskType(), "INDEX_FROM_TEXT")) {
            // 文本只在内存里，无法持久化重试
            log.warn("IndexService.scheduleRetry text类型不支持重试 taskId={},taskType={}", taskId, indexTask.getTaskType());
            return;
        }

        // 在业务线程里先捕获 UserContext——延迟回调时 ThreadLocal 已被清空
        Long userId = UserContext.getUserId();
        String departmentId = UserContext.getDepartmentId();
        String role = UserContext.getRole();

        retryScheduler.schedule(() -> {
            try {
                // ★ 延迟到期后，直接提交到 indexTaskExecutor 线程池
                //   executeFromMinio 内部会自行 set/clear UserContext
                indexTaskExecutor.execute(() -> executeFromMinio(taskId, docId, userId, departmentId, role));
            } catch (Exception e) {
                log.error("IndexService.scheduleRetry taskId={},error={}", taskId, e.getMessage(), e);
            }
        }, (long) Math.pow(2, retryCount - 1), TimeUnit.SECONDS);
    }
}