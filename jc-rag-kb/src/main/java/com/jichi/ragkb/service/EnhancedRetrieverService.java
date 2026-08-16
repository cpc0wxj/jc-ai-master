package com.jichi.ragkb.service;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Maps;
import com.jichi.ragkb.config.RagRetrievalProperties;
import com.jichi.ragkb.entity.DocChunk;
import com.jichi.ragkb.repository.DocChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 带查询改写的检索服务
 * 策略：原始问题 + HyDE 假设答案 → 各自向量化 → 多路检索 → RRF 融合
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnhancedRetrieverService {
    private final HybridRetrieverService hybridRetrieverService;
    private final QueryRewriterService queryRewriterService;
    private final EmbeddingService embeddingService;
    private final DocChunkRepository docChunkRepository;
    private final RagRetrievalProperties ragRetrievalProperties;

    private static final int RRF_K = 60;

    /**
     * 带 HyDE 的增强检索
     * 用原始问题 + HyDE 假设答案的向量分别检索，RRF 融合
     *
     * @param question 用户原始问题
     * @param kbIds    知识库 ID 列表
     * @param topN     返回数量
     */
    public List<HybridRetrieverService.ScoredChunk> retrieveWithHyde(String question, List<Long> kbIds, int topN) {
        // 原始问题的混合检索结果
        List<HybridRetrieverService.ScoredChunk> originalResults = hybridRetrieverService.retrieve(question, kbIds, ragRetrievalProperties.getVectorTopK());
        // HyDE 假设答案的向量检索结果
        String hydeAnswer = queryRewriterService.generateHypotheticalAnswer(question);
        float[] hydeEmbedding = embeddingService.embed(hydeAnswer);
        String hydeEmbeddingStr = StrUtil.format("[{}]", ArrayUtil.join(hydeEmbedding, ","));
        // 单次 SQL 完成多知识库检索：每个知识库取 TopK，不限制全局数量（后续 RRF 融合自行排序）
        List<DocChunk> hydeResultList = docChunkRepository.findByVectorSimilarityMultiKb(kbIds, hydeEmbeddingStr, ragRetrievalProperties.getVectorTopK(), null);
        log.info("EnhancedRetrieverService.retrieveWithHyde originalResults={},hydeResultList={}", originalResults.size(), hydeResultList.size());

        // RRF 融合两路结果
        Map<Long, Double> scoreMap = Maps.newLinkedHashMap();
        Map<Long, DocChunk> chunkMap = Maps.newHashMap();
        // 原始结果按已有 RRF 分数参与融合
        for (int i = 0; i < originalResults.size(); i++) {
            HybridRetrieverService.ScoredChunk scoredChunk = originalResults.get(i);
            double rrfScore = 1.0 / (RRF_K + i + 1);
            scoreMap.merge(scoredChunk.id(), rrfScore, Double::sum);
            chunkMap.put(scoredChunk.id(), scoredChunk.chunk());
        }
        // HyDE 结果
        for (int i = 0; i < hydeResultList.size(); i++) {
            DocChunk docChunk = hydeResultList.get(i);
            double rrfScore = 1.0 / (RRF_K + i + 1);
            scoreMap.merge(docChunk.getId(), rrfScore, Double::sum);
            chunkMap.put(docChunk.getId(), docChunk);
        }

        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topN)
                .map(e -> new HybridRetrieverService.ScoredChunk(chunkMap.get(e.getKey()), e.getValue()))
                .collect(Collectors.toList());
    }
}