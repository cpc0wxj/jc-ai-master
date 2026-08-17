package com.jichi.ragkb.service;

import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.collection.CollUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.jichi.ragkb.config.RerankerProperties;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Reranker 精排服务
 * 调用外部 Reranker API 对候选 chunk 进行精排，超时或失败时自动降级为 RRF 分数排序
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RerankerService {
    private final TokenMetrics tokenMetrics;
    private final WebClient.Builder webClientBuilder;
    private final RerankerProperties rerankerProperties;

    /**
     * 对候选 chunk 进行精排，返回按相关性分数排序的结果
     * 超时或 API 失败时自动降级（使用 RRF 分数排序）
     *
     * @param question        用户问题
     * @param scoredChunkList 候选 chunk（混合检索结果）
     * @param topN            精排后保留数量
     * @return 精排后的 ScoredChunk 列表
     */
    public List<HybridRetrieverService.ScoredChunk> rerank(String question, List<HybridRetrieverService.ScoredChunk> scoredChunkList, int topN) {
        if (CollectionUtils.isEmpty(scoredChunkList)) {
            return scoredChunkList;
        }
        // 候选数量已经不多，不需要精排
        if (scoredChunkList.size() <= topN) {
            return scoredChunkList;
        }

        try {
            List<HybridRetrieverService.ScoredChunk> reranked = callRerankApi(question, scoredChunkList, topN);
            log.info("RerankerService.rerank scoredChunkList={},returned={}", scoredChunkList.size(), reranked.size());
            return reranked;
        } catch (Exception e) {
            log.warn("RerankerService.rerank failed message={}", e.getMessage());
            // 降级：直接用 RRF 分数取 TopN
            return scoredChunkList.stream()
                    .limit(topN)
                    .collect(Collectors.toList());
        }
    }

    private List<HybridRetrieverService.ScoredChunk> callRerankApi(String question, List<HybridRetrieverService.ScoredChunk> scoredChunkList, int topN) {
        // 构建请求体（DashScope gte-rerank-v2 要求嵌套格式）
        List<String> docList = CollStreamUtil.toList(scoredChunkList, HybridRetrieverService.ScoredChunk::content);

        RerankInput rerankInput = new RerankInput()
                .setQuery(question)
                .setDocuments(docList);
        RerankParams rerankParams = new RerankParams()
                .setTopN(topN)
                .setReturnDocuments(false);
        RerankRequest rerankRequest = new RerankRequest()
                .setModel(rerankerProperties.getModel())
                .setInput(rerankInput)
                .setParameters(rerankParams);

        WebClient webClient = webClientBuilder
                .baseUrl(rerankerProperties.getEndpoint())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + rerankerProperties.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        RerankResponse rerankResponse = webClient.post()
                .bodyValue(rerankRequest)
                .retrieve()
                .bodyToMono(RerankResponse.class)
                .timeout(Duration.ofMillis(rerankerProperties.getTimeoutMs()))
                .block();

        Integer totalTokens = Optional.ofNullable(rerankResponse).map(RerankResponse::getUsage).map(RerankUsage::getTotalTokens).orElse(null);
        if (Objects.nonNull(totalTokens) && totalTokens > 0) {
            tokenMetrics.recordContextTokens(totalTokens);
        }

        // 按精排分数组装结果
        List<RerankResult> rerankResultList = Optional.ofNullable(rerankResponse).map(RerankResponse::getOutput).map(RerankOutput::getResults).orElse(null);
        CollUtil.sort(rerankResultList, Comparator.comparingDouble(RerankResult::getRelevanceScore).reversed());
        return CollStreamUtil.toList(rerankResultList,
                temp -> {
                    HybridRetrieverService.ScoredChunk scoredChunk = scoredChunkList.get(temp.getIndex());
                    return new HybridRetrieverService.ScoredChunk(scoredChunk.chunk(), temp.getRelevanceScore());
                });
    }

    // =================== 内部 DTO ===================

    @Getter
    @Setter
    @Accessors(chain = true)
    static class RerankRequest {
        private String model;
        private RerankInput input;
        private RerankParams parameters;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    static class RerankInput {
        private String query;
        private List<String> documents;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    static class RerankParams {
        @JsonProperty("top_n")
        private int topN;
        @JsonProperty("return_documents")
        private boolean returnDocuments;
    }

    @Data
    static class RerankResponse {
        private RerankOutput output;
        private RerankUsage usage;
    }

    @Data
    static class RerankOutput {
        private List<RerankResult> results;
    }

    @Data
    static class RerankResult {
        private int index;
        @JsonProperty("relevance_score")
        private double relevanceScore;
    }

    @Data
    static class RerankUsage {
        @JsonProperty("total_tokens")
        private int totalTokens;
    }
}