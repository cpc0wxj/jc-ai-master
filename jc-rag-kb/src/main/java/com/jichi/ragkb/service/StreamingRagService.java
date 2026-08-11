package com.jichi.ragkb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jichi.ragkb.config.RerankerProperties;
import com.jichi.ragkb.dto.RagResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 流式 RAG 服务
 * 通过 SSE 推送检索和生成结果，支持多轮对话上下文和查询缓存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamingRagService {
    private final EnhancedRetrieverService enhancedRetrieverService;
    private final RerankerService rerankerService;
    private final ConfidenceFilter confidenceFilter;
    private final ContextTrimmerService contextTrimmerService;
    private final SourceBuilder sourceBuilder;
    private final ChatSessionService chatSessionService;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final TokenMetrics tokenMetrics;
    private final QueryCacheService queryCacheService;
    private final RerankerProperties rerankerProperties;

    /**
     * 流式 RAG 查询，通过 SSE 推送结果
     */
    public void streamQuery(String question, List<Long> kbIds, String sessionId, SseEmitter emitter) {
        long start = System.currentTimeMillis();

        try {
            // 多轮追问（有历史）不走缓存；仅首轮可缓存
            List<Message> history = loadHistoryMessages(sessionId);
            boolean cacheable = history.isEmpty();

            // 首轮才查缓存——命中则直接把完整答案一次性推给前端，跳过检索和生成
            RagResponse cached = cacheable ? queryCacheService.getFromCache(question, kbIds) : null;
            if (Objects.nonNull(cached)) {
                emitter.send(SseEmitter.event().name("token").data(cached.getAnswer()));
                String doneData = objectMapper.writeValueAsString(
                        new DonePayload(cached.getSources(), 0));
                emitter.send(SseEmitter.event().name("done").data(doneData));
                emitter.complete();
                chatSessionService.saveMessage(sessionId, question, cached.getAnswer(), sourceBuilder.sourcesToJson(cached.getSources()), 0);
                return;
            }

            // 缓存未命中，走完整流式管道
            emitter.send(SseEmitter.event()
                    .name("status")
                    .data("{\"type\":\"RETRIEVING\",\"message\":\"正在检索知识库...\"}"));

            var candidates = enhancedRetrieverService.retrieveWithHyde(question, kbIds, 20);
            var reranked = rerankerService.rerank(question, candidates, rerankerProperties.getTopN());
            var filtered = confidenceFilter.filter(reranked);

            if (filtered.isEmpty()) {
                sendNotFound(emitter);
                return;
            }

            var trimmed = contextTrimmerService.trim(filtered);

            emitter.send(SseEmitter.event()
                    .name("status")
                    .data("{\"type\":\"GENERATING\",\"message\":\"已找到相关内容，正在生成回答...\"}"));

            String context = buildContext(trimmed);
            String systemPrompt = RagPromptTemplate.buildSystemPrompt(context, trimmed.size());

            StringBuilder fullAnswer = new StringBuilder();

            chatClient.prompt()
                    .system(systemPrompt)
                    .messages(history)
                    .user(question)
                    .stream()
                    .content()
                    .doOnNext(token -> {
                        try {
                            fullAnswer.append(token);
                            emitter.send(SseEmitter.event().name("token").data(token));
                        } catch (IOException e) {
                            log.warn("StreamingRagService.streamQuery sseSendFailed clientMayDisconnected");
                            throw new RuntimeException("SSE 连接断开");
                        }
                    })
                    .blockLast();

            String answer = fullAnswer.toString();
            tokenMetrics.recordGenerationTokens(contextTrimmerService.countTokens(answer));

            List<RagResponse.Source> sources = sourceBuilder.buildSources(answer, trimmed);
            String sourcesJson = sourceBuilder.sourcesToJson(sources);
            int latencyMs = (int) (System.currentTimeMillis() - start);

            chatSessionService.saveMessage(sessionId, question, answer, sourcesJson, latencyMs);

            // 只有首轮（无历史）才写缓存
            if (cacheable) {
                RagResponse response = new RagResponse()
                        .setAnswer(answer)
                        .setSources(sources)
                        .setLatencyMs(latencyMs);
                queryCacheService.putToCache(question, kbIds, response);
            }

            String doneData = objectMapper.writeValueAsString(new DonePayload(sources, latencyMs));
            emitter.send(SseEmitter.event().name("done").data(doneData));
            emitter.complete();

        } catch (Exception e) {
            log.error("StreamingRagService.streamQuery message={}", e.getMessage(), e);
            try {
                String errData = objectMapper.writeValueAsString(
                        Map.of("message", "生成过程中出现异常"));
                emitter.send(SseEmitter.event().name("error").data(errData));
                emitter.complete();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * 同步版本（供测试使用）
     */
    public RagResponse syncQuery(String question, List<Long> kbIds, String sessionId) {
        // 多轮追问（有历史）时同一句问题在不同上下文答案不同，不能走缓存；仅首轮可缓存
        List<Message> history = loadHistoryMessages(sessionId);
        boolean cacheable = history.isEmpty();

        // 先查缓存（仅首轮）
        RagResponse cached = cacheable ? queryCacheService.getFromCache(question, kbIds) : null;
        if (Objects.nonNull(cached)) {
            chatSessionService.saveMessage(sessionId, question, cached.getAnswer(), sourceBuilder.sourcesToJson(cached.getSources()), 0);
            return cached;
        }

        // 缓存未命中，走完整 RAG 管道
        long start = System.currentTimeMillis();

        var candidates = enhancedRetrieverService.retrieveWithHyde(question, kbIds, 20);
        var reranked = rerankerService.rerank(question, candidates, rerankerProperties.getTopN());
        var filtered = confidenceFilter.filter(reranked);

        if (filtered.isEmpty()) {
            return RagResponse.notFound();
        }

        var trimmed = contextTrimmerService.trim(filtered);
        String context = buildContext(trimmed);
        String systemPrompt = RagPromptTemplate.buildSystemPrompt(context, trimmed.size());

        String answer = chatClient.prompt()
                .system(systemPrompt)
                .messages(history)
                .user(question)
                .call()
                .content();

        tokenMetrics.recordGenerationTokens(contextTrimmerService.countTokens(answer));

        List<RagResponse.Source> sources = sourceBuilder.buildSources(answer, trimmed);
        String sourcesJson = sourceBuilder.sourcesToJson(sources);
        int latencyMs = (int) (System.currentTimeMillis() - start);

        chatSessionService.saveMessage(sessionId, question, answer, sourcesJson, latencyMs);

        RagResponse response = new RagResponse()
                .setAnswer(answer)
                .setSources(sources)
                .setLatencyMs(latencyMs);

        // 只有首轮（无历史）才写缓存
        if (cacheable) {
            queryCacheService.putToCache(question, kbIds, response);
        }
        return response;
    }

    /**
     * 把会话历史转成 Spring AI 的消息列表，喂给模型做多轮上下文
     */
    private List<Message> loadHistoryMessages(String sessionId) {
        return chatSessionService.getHistory(sessionId).stream()
                .map(m -> "USER".equals(m.getRole())
                        ? (Message) new UserMessage(m.getContent())
                        : (Message) new AssistantMessage(m.getContent()))
                .toList();
    }

    private String buildContext(List<HybridRetrieverService.ScoredChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            var sc = chunks.get(i);
            sb.append("[参考").append(i + 1).append("]");
            if (Objects.nonNull(sc.chunk().getSectionTitle())) {
                sb.append(" ").append(sc.chunk().getSectionTitle());
            }
            sb.append("\n").append(sc.content()).append("\n\n");
        }
        return sb.toString().strip();
    }

    private void sendNotFound(SseEmitter emitter) throws IOException {
        String msg = "在知识库中未找到与该问题相关的内容。请尝试用不同关键词提问，或联系相关部门。";
        emitter.send(SseEmitter.event().name("token").data(msg));
        emitter.send(SseEmitter.event().name("done").data("{\"sources\":[]}"));
        emitter.complete();
    }

    record DonePayload(List<RagResponse.Source> sources, int latencyMs) {
    }
}
