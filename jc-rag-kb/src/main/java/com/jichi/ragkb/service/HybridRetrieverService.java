package com.jichi.ragkb.service;

import cn.hutool.core.collection.CollStreamUtil;
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
    public List<ScoredChunk> retrieveWithPermissionCheck(String question, List<Long> requestedKbIds, int topN) {
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

        return CollStreamUtil.toList(kbIds,
                kbId -> {
                    KnowledgeBase knowledgeBase = knowledgeBaseRepository.findById(kbId);
                    boolean isPublic = Objects.nonNull(knowledgeBase) && Boolean.TRUE.equals(knowledgeBase.getIsPublic());
                    if (isPublic) {
                        return kbId;
                    }

                    boolean flag = kbPermissionRepository.existsByKbIdAndSubjectTypeAndSubjectId(kbId, "USER", String.valueOf(UserContext.getUserId()))
                            || kbPermissionRepository.existsByKbIdAndSubjectTypeAndSubjectId(kbId, "DEPARTMENT", UserContext.getDepartmentId());

                    return flag ? kbId : null;
                });
    }

    /**
     * RRF 融合两路检索结果
     * RRF (Reciprocal Rank Fusion) 是一种无参数的结果融合策略
     * 核心思想：每个 chunk 根据其在搜索结果中的排名获得分数，排名越靠前分数越高
     * 若同一个 chunk 在向量检索和全文检索中都出现，则累加其 RRF 分数
     *
     * @param vectorList   向量检索返回的结果列表（已按相似度降序）
     * @param fulltextList 全文检索返回的结果列表（已按相关性降序）
     * @return 按 RRF 分数排序的带分数 Chunk 列表（含分数信息）
     */
    private List<ScoredChunk> rrfMerge(List<DocChunk> vectorList, List<DocChunk> fulltextList) {
        // chunkId → RRF 分数累加值
        Map<Long, Double> scoreMap = new LinkedHashMap<>();
        // chunkId → DocChunk 对象，用于后续构建结果
        Map<Long, DocChunk> chunkMap = new HashMap<>();

        // ========== 处理向量检索结果，计算 RRF 分数 ==========
        for (int i = 0; i < vectorList.size(); i++) {
            DocChunk docChunk = vectorList.get(i);
            // RRF 公式：score = 1 / (k + rank + 1)
            // k=60 是平滑参数，rank 从 0 开始，+1 避免除数为 0
            double rrfScore = 1.0 / (RRF_K + i + 1);
            // 若该 chunk 已在 map 中（重复），则累加分数；否则新建
            scoreMap.merge(docChunk.getId(), rrfScore, Double::sum);
            // 缓存 chunk 对象（后续去重后直接取用）
            chunkMap.put(docChunk.getId(), docChunk);
        }

        // ========== 处理全文检索结果，分数累加 ==========
        for (int i = 0; i < fulltextList.size(); i++) {
            DocChunk docChunk = fulltextList.get(i);
            // RRF 公式相同
            double rrfScore = 1.0 / (RRF_K + i + 1);
            // 若该 chunk 在向量检索中也出现过，分数累加；否则新建
            scoreMap.merge(docChunk.getId(), rrfScore, Double::sum);
            chunkMap.put(docChunk.getId(), docChunk);
        }

        List<Map.Entry<Long, Double>> entryList = scoreMap.entrySet().stream()
                // 降序排列
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .toList();
        // 根据 chunkId 从 chunkMap 取出对应的 DocChunk 对象，包装为 ScoredChunk
        return CollStreamUtil.toList(entryList, temp -> new ScoredChunk(chunkMap.get(temp.getKey()), temp.getValue()));
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