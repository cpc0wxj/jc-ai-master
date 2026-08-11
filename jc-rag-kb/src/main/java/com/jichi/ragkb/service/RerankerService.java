package com.jichi.ragkb.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jichi.ragkb.config.RerankerProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
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
     * @param question   用户问题
     * @param candidates 候选 chunk（混合检索结果）
     * @param topN       精排后保留数量
     * @return 精排后的 ScoredChunk 列表
     */
    public List<HybridRetrieverService.ScoredChunk> rerank(
            String question,
            List<HybridRetrieverService.ScoredChunk> candidates,
            int topN) {

        if (candidates.isEmpty()) {
            return candidates;
        }
        if (candidates.size() <= topN) {
            // 候选数量已经不多，不需要精排
            return candidates;
        }

        try {
            List<HybridRetrieverService.ScoredChunk> reranked =
                    callRerankApi(question, candidates, topN);
            log.info("RerankerService.rerank candidates={},returned={}", candidates.size(), reranked.size());
            return reranked;

        } catch (Exception e) {
            log.warn("RerankerService.rerank failed message={}", e.getMessage());
            // 降级：直接用 RRF 分数取 TopN
            return candidates.stream()
                    .limit(topN)
                    .collect(Collectors.toList());
        }
    }

    private List<HybridRetrieverService.ScoredChunk> callRerankApi(
            String question,
            List<HybridRetrieverService.ScoredChunk> candidates,
            int topN) {

        // 构建请求体（DashScope gte-rerank-v2 要求嵌套格式）
        List<String> docs = candidates.stream()
                .map(HybridRetrieverService.ScoredChunk::content)
                .collect(Collectors.toList());

        RerankRequest request = new RerankRequest();
        request.setModel(rerankerProperties.getModel());
        request.setInput(new RerankInput(question, docs));
        request.setParameters(new RerankParams(topN, false));

        WebClient client = webClientBuilder
                .baseUrl(rerankerProperties.getEndpoint())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + rerankerProperties.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        RerankResponse response = client.post()
                .bodyValue(request)
                .retrieve()
                .bodyToMono(RerankResponse.class)
                .timeout(Duration.ofMillis(rerankerProperties.getTimeoutMs()))
                .block();

        if (Objects.isNull(response) || Objects.isNull(response.getOutput()) || Objects.isNull(response.getOutput().getResults())) {
            throw new RuntimeException("Reranker API 返回空结果");
        }

        if (Objects.nonNull(response.getUsage()) && response.getUsage().getTotalTokens() > 0) {
            tokenMetrics.recordContextTokens(response.getUsage().getTotalTokens());
        }

        // 按精排分数组装结果
        return response.getOutput().getResults().stream()
                .sorted(Comparator.comparingDouble(RerankResult::getRelevanceScore).reversed())
                .map(r -> {
                    HybridRetrieverService.ScoredChunk original = candidates.get(r.getIndex());
                    return new HybridRetrieverService.ScoredChunk(
                            original.chunk(),
                            r.getRelevanceScore()
                    );
                })
                .collect(Collectors.toList());
    }

    // =================== 内部 DTO ===================

    @Data
    static class RerankRequest {
        private String model;
        private RerankInput input;
        private RerankParams parameters;
    }

    @Data
    static class RerankInput {
        private String query;
        private List<String> documents;

        RerankInput(String query, List<String> documents) {
            this.query = query;
            this.documents = documents;
        }
    }

    @Data
    static class RerankParams {
        @JsonProperty("top_n")
        private int topN;
        @JsonProperty("return_documents")
        private boolean returnDocuments;

        RerankParams(int topN, boolean returnDocuments) {
            this.topN = topN;
            this.returnDocuments = returnDocuments;
        }
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