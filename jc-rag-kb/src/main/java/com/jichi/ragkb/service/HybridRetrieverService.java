package com.jichi.ragkb.service;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.jichi.ragkb.config.RagRetrievalProperties;
import com.jichi.ragkb.entity.DocChunk;
import com.jichi.ragkb.entity.KnowledgeBase;
import com.jichi.ragkb.exception.BizException;
import com.jichi.ragkb.repository.DocChunkRepository;
import com.jichi.ragkb.repository.KbPermissionRepository;
import com.jichi.ragkb.repository.KnowledgeBaseRepository;
import com.jichi.ragkb.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合检索服务
 * 向量检索 + 全文检索，RRF 融合排序
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridRetrieverService {
    private final EmbeddingService embeddingService;
    private final DocChunkRepository docChunkRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KbPermissionRepository kbPermissionRepository;
    private final RagRetrievalProperties ragRetrievalProperties;

    /**
     * RRF 平滑参数，通常取 60
     */
    private static final int RRF_K = 60;

    /**
     * 全文检索停用词（这些词在全文检索中无意义）
     */
    private static final List<String> STOP_WORDS = List.of("的", "了", "是", "在", "有", "和", "与", "或", "这", "那", "什么", "怎么", "如何", "为什么", "哪些", "怎样", "请问", "a", "an", "the", "is", "are", "what", "how");

    /**
     * 混合检索：向量检索 + 全文检索，RRF 融合排序
     *
     * @param question 用户问题（原始，未向量化）
     * @param kbIds    要查询的知识库 ID 列表
     * @param topN     最终返回的 chunk 数量（RRF 排序后取 TopN）
     * @return 按 RRF 分数排序的 chunk 列表（含分数信息）
     */
    public List<ScoredChunk> retrieve(String question, List<Long> kbIds, int topN) {
        // 向量检索
        float[] queryEmbedding = embeddingService.embed(question);
        String embeddingStr = StrUtil.format("[{}]", ArrayUtil.join(queryEmbedding, ","));

        // 单次 SQL 完成多知识库检索：每个知识库取 TopK，不限制全局数量（后续 RRF 融合自行排序）
        List<DocChunk> vectorResults = docChunkRepository.findByVectorSimilarityMultiKb(kbIds, embeddingStr, ragRetrievalProperties.getVectorTopK(), null);

        // 全文检索
        String tsQuery = buildTsQuery(question);
        List<DocChunk> fulltextResults = StringUtils.isNotBlank(tsQuery) ?
                docChunkRepository.findByFullTextSearchMultiKb(kbIds, tsQuery, ragRetrievalProperties.getFulltextTopK(), null) : Collections.emptyList();

        log.debug("HybridRetrieverService.retrieve vectorResults={},fulltextResults={}", vectorResults.size(), fulltextResults.size());

        // RRF 融合
        List<ScoredChunk> scoredChunkList = rrfMerge(vectorResults, fulltextResults);

        // 取 TopN
        scoredChunkList = scoredChunkList.stream()
                .limit(topN)
                .collect(Collectors.toList());

        log.info("HybridRetrieverService.retrieve topN={},scoredChunkList={}", topN, scoredChunkList.size());
        return scoredChunkList;
    }

    /**
     * 权限安全的混合检索——在调用前过滤 kbIds
     * 用户传入 kbIds，只有实际有权限的才会被检索
     */
    public List<ScoredChunk> retrieveWithPermissionCheck(
            String question, List<Long> requestedKbIds, int topN) {

        List<Long> allowedKbIds = filterAllowedKbIds(requestedKbIds);

        if (allowedKbIds.isEmpty()) {
            throw BizException.forbidden("您对所请求的知识库没有访问权限");
        }

        if (allowedKbIds.size() < requestedKbIds.size()) {
            List<Long> denied = requestedKbIds.stream()
                    .filter(id -> !allowedKbIds.contains(id))
                    .toList();
            log.warn("HybridRetrieverService.retrieveWithPermissionCheck userId={},deniedKbIds={}",
                    UserContext.getUserId(), denied);
        }

        return retrieve(question, allowedKbIds, topN);
    }

    private List<Long> filterAllowedKbIds(List<Long> kbIds) {
        if (UserContext.isAdmin()) {
            return kbIds;
        }

        String userId = String.valueOf(UserContext.getUserId());
        String deptId = UserContext.getDepartmentId();

        return kbIds.stream()
                .filter(kbId -> {
                    KnowledgeBase knowledgeBase = knowledgeBaseRepository.findById(kbId);
                    boolean isPublic = Objects.nonNull(knowledgeBase) && Boolean.TRUE.equals(knowledgeBase.getIsPublic());
                    if (isPublic) {
                        return true;
                    }

                    return kbPermissionRepository.existsByKbIdAndSubjectTypeAndSubjectId(kbId, "USER", userId)
                            || kbPermissionRepository.existsByKbIdAndSubjectTypeAndSubjectId(kbId, "DEPARTMENT", deptId);
                })
                .toList();
    }

    /**
     * RRF 融合两路结果
     * 去重：同一个 chunk 出现在两路结果中时，分数累加
     */
    private List<ScoredChunk> rrfMerge(List<DocChunk> vectorList, List<DocChunk> fulltextList) {
        // key: chunkId → RRF 分数
        Map<Long, Double> scoreMap = new LinkedHashMap<>();
        Map<Long, DocChunk> chunkMap = new HashMap<>();

        // 向量检索结果计分
        for (int rank = 0; rank < vectorList.size(); rank++) {
            DocChunk docChunk = vectorList.get(rank);
            double rrfScore = 1.0 / (RRF_K + rank + 1);
            scoreMap.merge(docChunk.getId(), rrfScore, Double::sum);
            chunkMap.put(docChunk.getId(), docChunk);
        }

        // 全文检索结果计分（累加）
        for (int rank = 0; rank < fulltextList.size(); rank++) {
            DocChunk docChunk = fulltextList.get(rank);
            double rrfScore = 1.0 / (RRF_K + rank + 1);
            scoreMap.merge(docChunk.getId(), rrfScore, Double::sum);
            chunkMap.put(docChunk.getId(), docChunk);
        }

        // 按 RRF 分数降序排列
        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .map(e -> new ScoredChunk(chunkMap.get(e.getKey()), e.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * 将问题转为 tsquery 格式
     * 例如："API 限流策略是什么" → "API & 限流 & 策略"
     */
    private String buildTsQuery(String query) {
        if (StringUtils.isBlank(query)) {
            return null;
        }

        // 按空格、标点切分
        String[] tokens = query.split("[\\s\\p{P}]+");

        List<String> keywordList = Arrays.stream(tokens)
                .map(String::strip)
                .filter(StringUtils::isNotBlank)
                .filter(t -> t.length() >= 2)
                .filter(t -> !STOP_WORDS.contains(t.toLowerCase()))
                .collect(Collectors.toList());

        // 若关键词list为空
        if (CollectionUtils.isEmpty(keywordList)) {
            // 降级：取整个查询的前20字符
            keywordList = List.of(query.substring(0, Math.min(20, query.length())));
        }

        // 用 & 连接（AND 查询），至少含所有关键词
        String tsQuery = String.join(" & ", keywordList);
        log.info("HybridRetrieverService.buildTsQuery query={},tsQuery={}", query, tsQuery);
        return tsQuery;
    }

    /**
     * 带分数的 Chunk 包装类
     */
    public record ScoredChunk(DocChunk chunk, double score) {
        public Long id() {
            return chunk.getId();
        }

        public String content() {
            return chunk.getContent();
        }
    }
}