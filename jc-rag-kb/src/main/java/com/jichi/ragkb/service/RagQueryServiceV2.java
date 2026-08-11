package com.jichi.ragkb.service;

import com.jichi.ragkb.config.RagRetrievalProperties;
import com.jichi.ragkb.entity.DocChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 升级版 RAG 查询服务（V2）
 * 使用混合检索（向量 + 全文）代替纯向量检索
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagQueryServiceV2 {
    private final ChatClient chatClient;
    private final HybridRetrieverService hybridRetrieverService;
    private final RagRetrievalProperties ragRetrievalProperties;

    public String query(String question, List<Long> kbIds) {
        int returnTopN = ragRetrievalProperties.getReturnTopN();

        // Step 1：混合检索
        List<HybridRetrieverService.ScoredChunk> scoredChunks = hybridRetrieverService.retrieve(question, kbIds, returnTopN);

        if (scoredChunks.isEmpty()) {
            return buildNotFoundResponse();
        }

        // Step 2：生成回答
        List<DocChunk> chunks = scoredChunks.stream()
                .map(HybridRetrieverService.ScoredChunk::chunk)
                .collect(Collectors.toList());

        return generateAnswer(question, chunks);
    }

    private String generateAnswer(String question, List<DocChunk> chunks) {
        String context = buildContext(chunks);
        String systemPrompt = buildSystemPrompt(context);

        return chatClient.prompt()
                .system(systemPrompt)
                .user(question)
                .call()
                .content();
    }

    private String buildContext(List<DocChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            DocChunk docChunk = chunks.get(i);
            sb.append("[参考").append(i + 1).append("]");
            if (Objects.nonNull(docChunk.getSectionTitle())) {
                sb.append(" ").append(docChunk.getSectionTitle());
            }
            sb.append("\n").append(docChunk.getContent()).append("\n\n");
        }
        return sb.toString().strip();
    }

    private String buildSystemPrompt(String context) {
        return """
                你是企业内部知识库的智能助手。根据以下参考内容回答用户问题。

                规则：
                1. 只基于参考内容回答，不使用自身知识推测
                2. 参考内容不足时，明确告知"未在知识库找到相关信息"
                3. 回答用中文，准确简洁
                4. 禁止编造参考内容之外的信息

                参考内容：
                ---
                %s
                ---
                """.formatted(context);
    }

    private String buildNotFoundResponse() {
        return "在您选择的知识库中未找到与该问题相关的内容。建议您：\n" +
               "1. 确认问题是否与知识库主题相关\n" +
               "2. 尝试用不同关键词提问\n" +
               "3. 联系相关部门获取准确信息";
    }
}