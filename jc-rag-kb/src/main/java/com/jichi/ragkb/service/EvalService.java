package com.jichi.ragkb.service;

import com.google.common.collect.Lists;
import com.jichi.ragkb.dto.EvalReport;
import com.jichi.ragkb.dto.RagResponse;
import com.jichi.ragkb.entity.EvalDataset;
import com.jichi.ragkb.entity.EvalResult;
import com.jichi.ragkb.repository.EvalDatasetRepository;
import com.jichi.ragkb.repository.EvalResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * RAG 评估服务
 * 运行自动化评估流水线，计算 Hit Rate、MRR、Faithfulness 等指标
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvalService {
    private final EvalDatasetRepository datasetRepository;
    private final EvalResultRepository resultRepository;
    private final EnhancedRetrieverService retriever;
    private final RerankerService rerankerService;
    private final HallucinationChecker hallucinationChecker;
    private final StreamingRagService ragService;

    /**
     * 运行完整评估流水线
     *
     * @param kbId        知识库 ID
     * @param evalVersion 评估版本标识（如 v1_chunk512_hybrid_reranker）
     * @return 评估摘要报告
     */
    public EvalReport runEvaluation(Long kbId, String evalVersion) {
        List<EvalDataset> questions = datasetRepository.findByKbId(kbId);
        if (questions.isEmpty()) {
            throw new RuntimeException("知识库 " + kbId + " 没有评估数据集，请先录入标准问题");
        }

        log.info("EvalService.runEvaluation kbId={},version={},questionCount={}", kbId, evalVersion, questions.size());

        List<EvalResult> results = Lists.newArrayList();
        int hits = 0;
        double mrr = 0.0;
        double totalFaithfulness = 0.0;
        int evalCount = 0;

        for (EvalDataset question : questions) {
            try {
                EvalResult result = evaluateOne(question, kbId, evalVersion);
                results.add(result);

                if (result.getHit()) {
                    hits++;
                }
                if (Objects.nonNull(result.getRank()) && result.getRank() > 0) {
                    mrr += 1.0 / result.getRank();
                }
                if (Objects.nonNull(result.getFaithfulness())) {
                    totalFaithfulness += result.getFaithfulness();
                    evalCount++;
                }

            } catch (Exception e) {
                log.error("EvalService.runEvaluation questionFailed,questionId={},error={}", question.getId(), e.getMessage());
            }
        }

        // 批量保存评估结果
        resultRepository.saveBatch(results);

        double hitRate = questions.isEmpty() ? 0 : (double) hits / questions.size();
        double mrrScore = questions.isEmpty() ? 0 : mrr / questions.size();
        double avgFaithfulness = evalCount == 0 ? 0 : totalFaithfulness / evalCount;

        EvalReport report = new EvalReport()
                .setKbId(kbId)
                .setEvalVersion(evalVersion)
                .setTotalQuestions(questions.size())
                .setHitCount(hits)
                .setHitRate(hitRate)
                .setMrr(mrrScore)
                .setAvgFaithfulness(avgFaithfulness)
                .setEvalAt(LocalDateTime.now());

        log.info("EvalService.runEvaluation completed,hitRate={}%,mrr={},faithfulness={}",
                String.format("%.2f", hitRate * 100),
                String.format("%.4f", mrrScore),
                String.format("%.4f", avgFaithfulness));

        return report;
    }

    private EvalResult evaluateOne(EvalDataset question, Long kbId, String evalVersion) {
        // 执行检索
        List<HybridRetrieverService.ScoredChunk> candidates =
                retriever.retrieveWithHyde(question.getQuestion(), List.of(kbId), 20);
        List<HybridRetrieverService.ScoredChunk> reranked =
                rerankerService.rerank(question.getQuestion(), candidates, 10);

        // 计算 Hit Rate 和 MRR
        Long[] expectedChunkIds = question.getExpectedChunkIds();
        boolean hit = false;
        int rank = 0;

        if (Objects.nonNull(expectedChunkIds) && expectedChunkIds.length > 0) {
            Set<Long> expected = Set.of(expectedChunkIds);
            for (int i = 0; i < reranked.size(); i++) {
                if (expected.contains(reranked.get(i).id())) {
                    hit = true;
                    rank = i + 1;
                    break;
                }
            }
        }

        // 生成回答并评估忠实性（仅对有参考答案的题跑）
        String actualAnswer = null;
        Double faithfulness = null;

        if (Objects.nonNull(question.getExpectedAnswer())) {
            // 每题用一次性会话 ID：syncQuery 会注入会话历史，
            // 复用同一个会话会把前一题的问答当历史带进来，污染评估结果
            String evalSessionId = "eval-" + UUID.randomUUID();
            RagResponse response = ragService.syncQuery(
                    question.getQuestion(), List.of(kbId), evalSessionId);
            actualAnswer = response.getAnswer();

            // 忠实性检测：用 rerank 后的 Top 结果做 context
            String context = reranked.stream()
                    .limit(5)
                    .map(HybridRetrieverService.ScoredChunk::content)
                    .collect(Collectors.joining("\n\n"));

            HallucinationChecker.FaithfulnessResult faithResult =
                    hallucinationChecker.check(question.getQuestion(), actualAnswer, context);
            faithfulness = faithResult.score();
        }

        return new EvalResult()
                .setDatasetId(question.getId())
                .setEvalVersion(evalVersion)
                .setHit(hit)
                .setRank(rank > 0 ? rank : null)
                .setActualAnswer(actualAnswer)
                .setFaithfulness(faithfulness)
                .setEvalAt(LocalDateTime.now());
    }

    /**
     * 对比不同评估版本的指标，生成对比报告
     */
    public List<EvalReport> compareVersions(Long kbId) {
        return resultRepository.aggregateByVersion(kbId);
    }
}