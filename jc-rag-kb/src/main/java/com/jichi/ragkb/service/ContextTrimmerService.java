package com.jichi.ragkb.service;

import com.google.common.collect.Lists;
import com.jichi.ragkb.config.RagContextProperties;
import com.jichi.ragkb.entity.DocChunk;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 上下文裁剪器
 * 在 Token 预算内尽量多保留高相关性 chunk
 *
 * 策略：按 Reranker 分数从高到低贪心添加 chunk，
 * 直到 Token 预算耗尽或所有 chunk 已添加完毕
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ContextTrimmerService {

    private final RagContextProperties ragContextProperties;
    private final TokenMetrics tokenMetrics;
    private Encoding tokenizer;

    @PostConstruct
    public void init() {
        // 使用 cl100k_base tokenizer（GPT-4 / qwen-plus 兼容）
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        this.tokenizer = registry.getEncoding(EncodingType.CL100K_BASE);
    }

    /**
     * 裁剪 chunk 列表，确保总 Token 不超过预算
     * 候选 chunk 已经按相关性排序（Reranker 分数），贪心选取
     *
     * @param candidates 已排序的 chunk 列表（相关性高的在前）
     * @return 裁剪后的 chunk 列表（顺序不变）
     */
    public List<HybridRetrieverService.ScoredChunk> trim(
            List<HybridRetrieverService.ScoredChunk> candidates) {

        int maxContextTokens = ragContextProperties.getMaxTokens();
        List<HybridRetrieverService.ScoredChunk> selected = Lists.newArrayList();
        int usedTokens = 0;

        for (HybridRetrieverService.ScoredChunk sc : candidates) {
            int chunkTokens = countTokens(sc.content());

            if (usedTokens + chunkTokens <= maxContextTokens) {
                selected.add(sc);
                usedTokens += chunkTokens;
            } else if (selected.isEmpty()) {
                // 第一个 chunk 就超了，截断后加入（至少要有一些内容）
                String truncated = truncateToTokens(sc.content(),
                        maxContextTokens - usedTokens);
                if (StringUtils.isNotBlank(truncated)) {
                    // 构建截断后的 DocChunk 副本，替换 content 为截断版本
                    DocChunk truncatedChunk = new DocChunk()
                            .setId(sc.chunk().getId())
                            .setDocId(sc.chunk().getDocId())
                            .setKbId(sc.chunk().getKbId())
                            .setChunkIndex(sc.chunk().getChunkIndex())
                            .setContent(truncated)
                            .setPageNum(sc.chunk().getPageNum())
                            .setSectionTitle(sc.chunk().getSectionTitle())
                            .setTokenCount(countTokens(truncated))
                            .setDocVersion(sc.chunk().getDocVersion());

                    selected.add(new HybridRetrieverService.ScoredChunk(
                            truncatedChunk, sc.score()));
                    usedTokens += countTokens(truncated);
                }
                break;
            } else {
                // 已有内容，后面的 chunk 放不下了
                break;
            }
        }

        log.info("ContextTrimmerService.trim candidates={},selected={},usedTokens={},maxTokens={}",
                candidates.size(), selected.size(), usedTokens, maxContextTokens);

        tokenMetrics.recordContextTokens(usedTokens);

        return selected;
    }

    /**
     * 统计文本的 Token 数
     * 对于中文，jtokkit 使用 cl100k 编码，1个汉字约 1-2 Token
     */
    public int countTokens(String text) {
        if (Objects.isNull(text) || StringUtils.isBlank(text)) {
            return 0;
        }
        return tokenizer.encode(text).size();
    }

    /**
     * 截断文本到不超过指定 Token 数，在句子边界处截断
     */
    private String truncateToTokens(String text, int maxTokens) {
        if (maxTokens <= 0) {
            return "";
        }
        if (countTokens(text) <= maxTokens) {
            return text;
        }

        // 按句子分割，贪心添加
        String[] sentences = text.split("(?<=[。！？\\n])");
        StringBuilder result = new StringBuilder();
        int tokens = 0;

        for (String sentence : sentences) {
            int sentenceTokens = countTokens(sentence);
            if (tokens + sentenceTokens <= maxTokens) {
                result.append(sentence);
                tokens += sentenceTokens;
            } else {
                break;
            }
        }

        return result.toString();
    }
}
