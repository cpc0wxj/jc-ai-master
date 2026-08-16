package com.jichi.ragkb.service;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.jichi.ragkb.config.RagRetrievalProperties;
import com.jichi.ragkb.entity.DocChunk;
import com.jichi.ragkb.repository.DocChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 基础 RAG 查询服务（V1）
 * 向量检索 + 生成回答，最简单的 RAG 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagQueryService {
    private final ChatClient chatClient;
    private final EmbeddingService embeddingService;
    private final DocChunkRepository docChunkRepository;
    private final RagRetrievalProperties ragRetrievalProperties;

    /**
     * 基础 RAG 查询：向量检索 + 生成回答
     *
     * @param question 用户问题
     * @param kbIds 要查询的知识库 ID 列表
     * @return 生成的答案
     */
    public String query(String question, List<Long> kbIds) {
        int vectorTopK = ragRetrievalProperties.getVectorTopK();
        // 向量化问题并拼接为向量字符串
        float[] queryEmbedding = embeddingService.embed(question);
        String embeddingStr = StrUtil.format("[{}]", ArrayUtil.join(queryEmbedding, ","));

        // 向量检索
        List<DocChunk> docChunkList = docChunkRepository.findByVectorSimilarityMultiKb(kbIds, embeddingStr, vectorTopK, vectorTopK);
        log.debug("RagQueryService.query kbIds={}, retrieved={}", kbIds, docChunkList.size());

        if (CollectionUtils.isEmpty(docChunkList)) {
            return "在您选择的知识库中未找到与该问题相关的内容。请确认问题是否与知识库的主题相关，或尝试用不同的表达方式提问。";
        }

        // 组装 Prompt 并生成回答
        return generateAnswer(question, docChunkList);
    }

    /**
     * 组装 System Prompt + Context，调用模型生成答案
     */
    protected String generateAnswer(String question, List<DocChunk> docChunkList) {
        String context = IntStream.range(0, docChunkList.size())
                .mapToObj(i -> {
                    DocChunk chunk = docChunkList.get(i);
                    String title = StrUtil.isBlank(chunk.getSectionTitle()) ? "" : " " + chunk.getSectionTitle();
                    return StrUtil.format("[参考{}]{}\n{}", i + 1, title, chunk.getContent());
                })
                .collect(Collectors.joining("\n\n"))
                .strip();

        String systemPrompt = """
                你是企业内部知识库的智能助手。你的工作是根据提供的参考文档内容，准确回答员工的问题。
                
                重要规则：
                1. 只根据提供的【参考内容】回答问题，不要使用自己的知识进行推测或补充
                2. 如果参考内容不足以回答问题，明确告诉用户"在知识库中未找到相关信息"，并建议用户联系相关部门
                3. 回答要准确、简洁，用中文回答
                4. 如果参考内容涉及多个文档，综合各文档回答
                5. 禁止编造不在参考内容中的信息
                
                参考内容如下：
                ---
                %s
                ---
                """.formatted(context);

        log.debug("RagQueryService.generateAnswer contextLength={}", context.length());

        long start = System.currentTimeMillis();
        String answer = chatClient.prompt()
                .system(systemPrompt)
                .user(question)
                .call()
                .content();
        log.info("RagQueryService.generateAnswer elapsed={},answerLength={}", System.currentTimeMillis() - start, Optional.ofNullable(answer).map(String::length).orElse(0));

        return answer;
    }
}