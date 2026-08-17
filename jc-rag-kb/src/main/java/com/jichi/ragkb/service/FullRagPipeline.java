package com.jichi.ragkb.service;

import cn.hutool.core.util.StrUtil;
import com.jichi.ragkb.config.RerankerProperties;
import com.jichi.ragkb.dto.RagResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 完整 RAG 管线
 * 增强检索 → Reranker 精排 → 置信度过滤 → 上下文裁剪 → 生成回答 → 引用解析 → 幻觉检测
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FullRagPipeline {
    private final EnhancedRetrieverService enhancedRetrieverService;
    private final RerankerService rerankerService;
    private final ConfidenceFilter confidenceFilter;
    private final ContextTrimmerService contextTrimmerService;
    private final SourceBuilder sourceBuilder;
    private final HallucinationChecker hallucinationChecker;
    private final ChatClient chatClient;
    private final RerankerProperties rerankerProperties;

    public RagResponse query(String question, List<Long> kbIds) {
        long pipelineStart = System.currentTimeMillis();

        // 增强检索（混合检索 + HyDE）
        List<HybridRetrieverService.ScoredChunk> scoredChunkList = enhancedRetrieverService.retrieveWithHyde(question, kbIds, 20);
        if (CollectionUtils.isEmpty(scoredChunkList)) {
            return RagResponse.notFound();
        }
        // Reranker 精排
        scoredChunkList = rerankerService.rerank(question, scoredChunkList, rerankerProperties.getTopN());
        // 置信度过滤
        scoredChunkList = confidenceFilter.filter(scoredChunkList);
        if (CollectionUtils.isEmpty(scoredChunkList)) {
            return RagResponse.notFound();
        }
        // 上下文裁剪（控制 Token 预算）
        scoredChunkList = contextTrimmerService.trim(scoredChunkList);
        // 构建带引用编号的 context + 用 RagPromptTemplate 生成 System Prompt
        String context = buildContext(scoredChunkList);
        // 生成答案
        String systemPrompt = RagPromptTemplate.buildSystemPrompt(context, scoredChunkList.size());
        String answer = chatClient.prompt()
                .system(systemPrompt)
                .user(question)
                .call()
                .content();

        // 用 SourceBuilder 解析引用标注，关联到文档信息
        List<RagResponse.Source> sourceList = sourceBuilder.buildSources(answer, scoredChunkList);

        // 幻觉检测（抽样，每 5 次查询跑 1 次，避免增加太多成本）
        if (System.currentTimeMillis() % 5 == 0) {
            var faithResult = hallucinationChecker.check(question, answer, context);
            if (!faithResult.isFaithful()) {
                log.warn("FullRagPipeline.query hallucinationDetected score={},reason={}", faithResult.score(), faithResult.reason());
            }
        }

        long elapsed = System.currentTimeMillis() - pipelineStart;
        log.info("FullRagPipeline.query question={},elapsed={},sourceList={}", question.substring(0, Math.min(30, question.length())), elapsed, sourceList.size());

        return new RagResponse()
                .setAnswer(answer)
                .setSources(sourceList)
                .setLatencyMs((int) elapsed);
    }

    private String buildContext(List<HybridRetrieverService.ScoredChunk> scoredChunkList) {
        return IntStream.range(0, scoredChunkList.size())
                .mapToObj(i -> {
                    HybridRetrieverService.ScoredChunk sc = scoredChunkList.get(i);
                    String title = StrUtil.isBlank(sc.chunk().getSectionTitle()) ? "" : " " + sc.chunk().getSectionTitle();
                    return StrUtil.format("[参考{}]{}\n{}", i + 1, title, sc.content());
                })
                .collect(Collectors.joining("\n\n"))
                .strip();
    }
}