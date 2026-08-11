package com.jichi.ragkb.service;

import com.jichi.ragkb.entity.DocChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * RAG 查询服务（V3）
 * 使用 HyDE 增强检索（假设性回答 + 多路向量检索 + RRF 融合）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagQueryServiceV3 {
    private final ChatClient chatClient;
    private final EnhancedRetrieverService enhancedRetrieverService;

    public String query(String question, List<Long> kbIds) {
        List<HybridRetrieverService.ScoredChunk> scoredChunks = enhancedRetrieverService.retrieveWithHyde(question, kbIds, 5);

        if (scoredChunks.isEmpty()) {
            return "在您选择的知识库中未找到与该问题相关的内容。";
        }

        List<DocChunk> chunks = scoredChunks.stream()
                .map(HybridRetrieverService.ScoredChunk::chunk)
                .collect(Collectors.toList());

        return generateAnswer(question, chunks);
    }

    private String generateAnswer(String question, List<DocChunk> chunks) {
        String context = buildContext(chunks);

        String systemPrompt = """
                你是企业内部知识库的智能助手。根据提供的参考内容回答问题。
                规则：只根据参考内容回答，不要编造；如果参考内容不够，告诉用户未找到相关信息。

                参考内容：
                ---
                %s
                ---
                """.formatted(context);

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
            sb.append(String.format("[参考%d]", i + 1));
            if (Objects.nonNull(docChunk.getSectionTitle())) {
                sb.append(" ").append(docChunk.getSectionTitle());
            }
            sb.append("\n").append(docChunk.getContent()).append("\n\n");
        }
        return sb.toString().strip();
    }
}