package com.jichi.ragkb.service;

import com.jichi.ragkb.security.UserContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * RAG 查询的 Token 消耗统计器
 *
 * 双写策略：
 * 1. Micrometer Counter —— 全局指标，供 Prometheus / Grafana 监控使用，重启归零
 * 2. Redis Hash —— 按用户维度持久化，重启不丢失，供前端监控面板展示
 *
 * 必须用 StringRedisTemplate，不能用自定义的 RedisTemplate<String, Object>
 * 因为 GenericJackson2JsonRedisSerializer 和 HINCRBY 写入的原生数字格式不兼容
 *
 * Redis Key 格式：rag:token-stats:{userId}
 * Hash Fields：embeddingTokens / contextTokens / generationTokens
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenMetrics {
    private final MeterRegistry meterRegistry;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Redis Key 前缀，拼接 userId 后构成完整 Key：rag:token-stats:{userId}
     */
    private static final String REDIS_KEY_PREFIX = "rag:token-stats:";

    // Embedding 向量化消耗的 Token 计数器（全局）
    private Counter embeddingTokenCounter;
    // 检索上下文（Context）消耗的 Token 计数器（全局）
    private Counter contextTokenCounter;
    // 模型生成回答消耗的 Token 计数器（全局）
    private Counter generationTokenCounter;

    /**
     * 注册三个全局 Token 指标到 Micrometer
     * 指标名分别为 rag.tokens.embedding / rag.tokens.context / rag.tokens.generation
     */
    @PostConstruct
    public void init() {
        // Embedding 向量化消耗的 Token 总数
        embeddingTokenCounter = Counter.builder("rag.tokens.embedding")
                .description("Embedding 消耗的 Token 总数")
                .register(meterRegistry);
        // 传入模型的 Context Token 总数
        contextTokenCounter = Counter.builder("rag.tokens.context")
                .description("传入模型的 Context Token 总数")
                .register(meterRegistry);
        // 模型生成消耗的 Token 总数
        generationTokenCounter = Counter.builder("rag.tokens.generation")
                .description("模型生成消耗的 Token 总数")
                .register(meterRegistry);
    }

    /**
     * 记录一次 Embedding 向量化消耗的 Token 数（双写 Micrometer + Redis）
     *
     * @param tokens 本次消耗的 Token 数
     */
    public void recordEmbeddingTokens(int tokens) {
        log.info("TokenMetrics.recordEmbeddingTokens tokens={}", tokens);
        embeddingTokenCounter.increment(tokens);
        incrementRedis("embeddingTokens", tokens);
    }

    /**
     * 记录一次检索上下文消耗的 Token 数（双写 Micrometer + Redis）
     *
     * @param tokens 本次消耗的 Token 数
     */
    public void recordContextTokens(int tokens) {
        log.info("TokenMetrics.recordContextTokens tokens={}", tokens);
        contextTokenCounter.increment(tokens);
        incrementRedis("contextTokens", tokens);
    }

    /**
     * 记录一次模型生成消耗的 Token 数（双写 Micrometer + Redis）
     *
     * @param tokens 本次消耗的 Token 数
     */
    public void recordGenerationTokens(int tokens) {
        log.info("TokenMetrics.recordGenerationTokens tokens={}", tokens);
        generationTokenCounter.increment(tokens);
        incrementRedis("generationTokens", tokens);
    }

    /**
     * 从 Redis 读取指定用户的 Token 统计
     *
     * @param userId 用户 ID
     * @param field  统计字段（embeddingTokens / contextTokens / generationTokens）
     * @return 累计 Token 数，无记录或值格式异常时返回 0
     */
    public long getUserTokens(Long userId, String field) {
        // 从 Hash 中读取原始字符串值
        String val = (String) stringRedisTemplate.opsForHash()
                .get(REDIS_KEY_PREFIX + userId, field);
        log.info("TokenMetrics.getUserTokens userId={},field={},val={}", userId, field, val);
        if (Objects.nonNull(val)) {
            try {
                return Long.parseLong(val);
            }
            // 值非数字格式（脏数据），当作无记录处理
            catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    /**
     * 对当前用户的 Redis Hash 字段原子自增（HINCRBY）
     * 统计失败仅记录日志，不影响主流程
     *
     * @param field 统计字段
     * @param delta 本次增量
     */
    private void incrementRedis(String field, int delta) {
        try {
            // 从用户上下文获取当前用户 ID，拼接完整 Key
            String key = REDIS_KEY_PREFIX + UserContext.getUserId();
            log.info("TokenMetrics.incrementRedis key={},field={},delta={}", key, field, delta);
            stringRedisTemplate.opsForHash().increment(key, field, delta);
        }
        // Redis 异常时吞掉，保证 Token 统计不影响业务主流程
        catch (Exception e) {
            log.error("TokenMetrics.incrementRedis message={}", e.getMessage(), e);
        }
    }
}