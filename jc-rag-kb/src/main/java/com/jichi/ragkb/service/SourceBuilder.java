package com.jichi.ragkb.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.jichi.ragkb.dto.RagResponse;
import com.jichi.ragkb.entity.KbDocument;
import com.jichi.ragkb.repository.KbDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 来源构建器
 * 从模型回答中解析引用标注，关联到文档信息，构建来源列表
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SourceBuilder {
    private final CitationParser citationParser;
    private final KbDocumentRepository kbDocumentRepository;
    private final ObjectMapper objectMapper;

    /**
     * 从模型回答中解析引用，关联到文档信息，构建来源列表
     *
     * @param answer 模型回答（含引用标注）
     * @param chunks 传入模型的 chunk 列表（顺序对应 [参考N] 编号）
     * @return 被引用的来源列表（前端展示用）
     */
    public List<RagResponse.Source> buildSources(String answer, List<HybridRetrieverService.ScoredChunk> chunks) {
        // 解析被引用的 chunk 索引（1-based）
        Set<Integer> citedIndices = citationParser.extractCitedIndices(answer);

        // 如果模型没有标注引用（或标注不完整），把所有 chunk 都列为来源
        if (citedIndices.isEmpty()) {
            log.debug("SourceBuilder.buildSources noCitation usingAllChunks");
            citedIndices = new LinkedHashSet<>();
            for (int i = 1; i <= chunks.size(); i++) {
                citedIndices.add(i);
            }
        }

        // 批量查询文档信息（避免 N+1 查询）
        Set<Long> docIds = chunks.stream()
                .map(scoredChunk -> scoredChunk.chunk().getDocId())
                .collect(Collectors.toSet());
        Map<Long, KbDocument> docMap = kbDocumentRepository.findAllById(docIds).stream()
                .collect(Collectors.toMap(KbDocument::getId, d -> d));

        // 组装来源信息
        List<RagResponse.Source> sources = Lists.newArrayList();
        for (int idx : citedIndices) {
            if (idx < 1 || idx > chunks.size()) {
                continue;
            }

            HybridRetrieverService.ScoredChunk scoredChunk = chunks.get(idx - 1);
            KbDocument kbDocument = docMap.get(scoredChunk.chunk().getDocId());

            sources.add(new RagResponse.Source()
                    .setChunkId(scoredChunk.id())
                    .setDocId(scoredChunk.chunk().getDocId())
                    .setDocName(Objects.nonNull(kbDocument) ? kbDocument.getFileName() : "未知文档")
                    .setPageNum(scoredChunk.chunk().getPageNum())
                    .setSectionTitle(scoredChunk.chunk().getSectionTitle())
                    .setExcerpt(scoredChunk.content().substring(0, Math.min(200, scoredChunk.content().length())))
                    .setScore(scoredChunk.score()));
        }

        return sources;
    }

    /**
     * 序列化来源列表为 JSON 字符串，用于存储到 chat_message.sources 字段
     */
    public String sourcesToJson(List<RagResponse.Source> sources) {
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (JsonProcessingException e) {
            log.error("SourceBuilder.sourcesToJson message={}", e.getMessage());
            return "[]";
        }
    }
}