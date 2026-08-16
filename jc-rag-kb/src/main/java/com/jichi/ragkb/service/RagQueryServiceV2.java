package com.jichi.ragkb.service;

import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.util.StrUtil;
import com.jichi.ragkb.config.RagRetrievalProperties;
import com.jichi.ragkb.entity.DocChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

        // 混合检索
        List<HybridRetrieverService.ScoredChunk> scoredChunkList = hybridRetrieverService.retrieve(question, kbIds, returnTopN);

        if (CollectionUtils.isEmpty(scoredChunkList)) {
            return buildNotFoundResponse();
        }

        // 生成回答
        List<DocChunk> docChunkList = CollStreamUtil.toList(scoredChunkList, HybridRetrieverService.ScoredChunk::chunk);

        return generateAnswer(question, docChunkList);
    }

    private String generateAnswer(String question, List<DocChunk> docChunkList) {
        String context = IntStream.range(0, docChunkList.size())
                .mapToObj(i -> {
                    DocChunk chunk = docChunkList.get(i);
                    String title = Objects.nonNull(chunk.getSectionTitle()) ? " " + chunk.getSectionTitle() : "";
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

        return chatClient.prompt()
                .system(systemPrompt)
                .user(question)
                .call()
                .content();
    }

    private String buildNotFoundResponse() {
        return "在您选择的知识库中未找到与该问题相关的内容。建议您：\n" +
                "1. 确认问题是否与知识库主题相关\n" +
                "2. 尝试用不同关键词提问\n" +
                "3. 联系相关部门获取准确信息";
    }
}