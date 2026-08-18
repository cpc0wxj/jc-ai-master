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
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 上下文裁剪器
 * 在 Token 预算内尽量多保留高相关性 chunk
 * 策略：按 Reranker 分数从高到低贪心添加 chunk，
 * 直到 Token 预算耗尽或所有 chunk 已添加完毕
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextTrimmerService {
    private Encoding encoding;
    private final TokenMetrics tokenMetrics;
    private final RagContextProperties ragContextProperties;

    @PostConstruct
    public void init() {
        // 使用 cl100k_base tokenizer（GPT-4 / qwen-plus 兼容）
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        this.encoding = registry.getEncoding(EncodingType.CL100K_BASE);
    }

    /**
     * 裁剪 chunk 列表，确保总 Token 不超预算
     * 候选 chunk 已经按相关性排序（Reranker 分数），贪心选取
     * 处理策略:
     * 预算充足：chunk 直接加入选中列表
     * 第一个 chunk 就超预算：截断后加入，保证上下文不为空
     * 已有内容但放不下：停止选取（后续 chunk 相关性更低，直接丢弃）
     *
     * @param scoredChunkList 已排序的 chunk 列表（相关性高的在前）
     * @return 裁剪后的 chunk 列表（顺序不变，总 Token 不超过预算）
     */
    public List<HybridRetrieverService.ScoredChunk> trim(List<HybridRetrieverService.ScoredChunk> scoredChunkList) {
        // 已选中的 chunk 列表
        List<HybridRetrieverService.ScoredChunk> resultList = Lists.newArrayList();
        // 已占用的 Token 数
        int usedTokens = 0;

        for (HybridRetrieverService.ScoredChunk scoredChunk : scoredChunkList) {
            // 当前 chunk 的 Token 数
            int chunkTokens = countTokens(scoredChunk.content());

            if (usedTokens + chunkTokens <= ragContextProperties.getMaxTokens()) {
                // 预算充足，直接加入
                resultList.add(scoredChunk);
                usedTokens += chunkTokens;
            }
            // 若第一个chunk就超了
            else if (CollectionUtils.isEmpty(resultList)) {
                // 截断后加入（至少要有一些内容）
                // 截断到剩余预算大小，尽量保留相关性最高的内容
                String truncated = truncateToTokens(scoredChunk.content(), ragContextProperties.getMaxTokens() - usedTokens);
                if (StringUtils.isNotBlank(truncated)) {
                    // 构建截断后的 DocChunk 副本，替换 content 为截断版本，其余字段保持不变
                    DocChunk docChunk = new DocChunk()
                            .setId(scoredChunk.chunk().getId())
                            .setDocId(scoredChunk.chunk().getDocId())
                            .setKbId(scoredChunk.chunk().getKbId())
                            .setChunkIndex(scoredChunk.chunk().getChunkIndex())
                            .setContent(truncated)
                            .setPageNum(scoredChunk.chunk().getPageNum())
                            .setSectionTitle(scoredChunk.chunk().getSectionTitle())
                            .setTokenCount(countTokens(truncated))
                            .setDocVersion(scoredChunk.chunk().getDocVersion());

                    // 用截断后的 chunk 替换原 chunk 加入选中列表，分数保持不变
                    resultList.add(new HybridRetrieverService.ScoredChunk(docChunk, scoredChunk.score()));
                    usedTokens += countTokens(truncated);
                }
                // 截断后预算已用尽，结束循环
                break;
            } else {
                // 已有内容，后面的 chunk 放不下了
                break;
            }
        }

        log.info("ContextTrimmerService.trim scoredChunkList={},resultList={},usedTokens={},maxTokens={}", scoredChunkList.size(), resultList.size(), usedTokens, ragContextProperties.getMaxTokens());

        // 记录本次上下文实际使用的 Token 数（用于监控统计）
        tokenMetrics.recordContextTokens(usedTokens);

        return resultList;
    }

    /**
     * 截断文本到不超过指定 Token 数，在句子边界处截断
     */
    private String truncateToTokens(String text, int maxTokens) {
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

    /**
     * 统计文本的 Token 数
     * 对于中文，jtokkit 使用 cl100k 编码，1个汉字约 1-2 Token
     */
    public int countTokens(String text) {
        return StringUtils.isBlank(text) ? encoding.encode(text).size() : 0;
    }
}