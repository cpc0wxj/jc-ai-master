package com.jichi.ragkb.service;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
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

        // 路线2：HyDE 假设答案的向量检索结果
        String hydeAnswer = queryRewriterService.generateHypotheticalAnswer(question);
        float[] hydeEmbedding = embeddingService.embed(hydeAnswer);
        String hydeEmbeddingStr = StrUtil.format("[{}]", ArrayUtil.join(hydeEmbedding, ","));

        // 单次 SQL 完成多知识库检索：每个知识库取 TopK，不限制全局数量（后续 RRF 融合自行排序）
        List<DocChunk> hydeResults = docChunkRepository.findByVectorSimilarityMultiKb(kbIds, hydeEmbeddingStr, ragRetrievalProperties.getVectorTopK(), null);

        log.debug("EnhancedRetrieverService.retrieveWithHyde originalResults={},hydeResults={}",
                originalResults.size(), hydeResults.size());

        // RRF 融合两路结果
        Map<Long, Double> scoreMap = new LinkedHashMap<>();
        Map<Long, DocChunk> chunkMap = new HashMap<>();

        // 原始结果按已有 RRF 分数参与融合
        for (int rank = 0; rank < originalResults.size(); rank++) {
            HybridRetrieverService.ScoredChunk scoredChunk = originalResults.get(rank);
            double rrfScore = 1.0 / (RRF_K + rank + 1);
            scoreMap.merge(scoredChunk.id(), rrfScore, Double::sum);
            chunkMap.put(scoredChunk.id(), scoredChunk.chunk());
        }

        // HyDE 结果
        for (int rank = 0; rank < hydeResults.size(); rank++) {
            DocChunk docChunk = hydeResults.get(rank);
            double rrfScore = 1.0 / (RRF_K + rank + 1);
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