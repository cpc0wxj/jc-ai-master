package com.jichi.ragkb.service;

import cn.hutool.core.collection.CollStreamUtil;
import com.jichi.ragkb.config.RagRetrievalProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 置信度过滤器
 * 过滤掉低分 chunk，避免不相关内容影响生成质量
 * 由 FullRagPipeline 注入使用，不单独暴露 HTTP
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfidenceFilter {
    private final RagRetrievalProperties ragRetrievalProperties;

    /**
     * 过滤低置信度的 chunk
     * 如果过滤后为空，保留分数最高的 1 个（不能完全没有内容）
     */
    public List<HybridRetrieverService.ScoredChunk> filter(List<HybridRetrieverService.ScoredChunk> scoredChunkList) {
        if (CollectionUtils.isEmpty(scoredChunkList)) {
            return Collections.emptyList();
        }

        List<HybridRetrieverService.ScoredChunk> filteredChunkList = CollStreamUtil.toList(scoredChunkList,
                temp -> temp.score() >= ragRetrievalProperties.getMinScore() ? temp : null);

        // 若过滤结果为空
        if (CollectionUtils.isEmpty(filteredChunkList)) {
            // 至少保留分数最高的 1 个（上游已排序，但以防万一用 max 取最高分）
            HybridRetrieverService.ScoredChunk scoredChunk = scoredChunkList.stream()
                    .max(Comparator.comparingDouble(HybridRetrieverService.ScoredChunk::score))
                    .orElse(scoredChunkList.getFirst());
            log.debug("ConfidenceFilter.filter allBelowThreshold minScore={},bestScore={}", ragRetrievalProperties.getMinScore(), scoredChunk.score());
            filteredChunkList = List.of(scoredChunk);
        }

        int filteredCount = scoredChunkList.size() - filteredChunkList.size();
        if (filteredCount > 0) {
            log.debug("ConfidenceFilter.filter filteredCount={}", filteredCount);
        }

        return filteredChunkList;
    }
}