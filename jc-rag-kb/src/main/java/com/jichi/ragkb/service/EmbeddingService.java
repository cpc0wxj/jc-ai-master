package com.jichi.ragkb.service;

import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jichi.ragkb.config.RagCacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * 向量化服务
 * 负责将文本转换为向量表示，支持批量处理与 Redis 缓存，避免重复调用 Embedding API
 * 核心流程：先查 Redis 缓存，缓存未命中的文本批量调 API，结果回写缓存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {
    /**
     * Spring AI 提供的嵌入模型，用于调用底层 Embedding API
     */
    private final EmbeddingModel embeddingModel;
    /**
     * Redis 操作模板，用于读写向量缓存
     */
    private final StringRedisTemplate redisTemplate;
    /**
     * RAG 缓存配置，提供向量缓存的 TTL 等参数
     */
    private final RagCacheProperties ragCacheProperties;
    /**
     * Redis 缓存 Key 前缀，v1 为版本号，便于缓存整体失效时升级版本
     */
    private static final String CACHE_PREFIX = "emb:v1:";
    /**
     * 单次 API 请求的最大文本数量，避免单次请求过大导致超限或超时
     */
    private static final int BATCH_SIZE = 20;

    /**
     * 批量向量化，带 Redis 缓存
     *
     * @param textList 待向量化的文本列表
     * @return 与输入顺序对应的向量列表，空输入返回空列表
     */
    public List<float[]> embedBatch(List<String> textList) {
        // 空列表快速返回，避免无意义的缓存查询和 API 调用
        if (CollectionUtils.isEmpty(textList)) {
            return Collections.emptyList();
        }

        // 结果容器：key 为原始下标，value 为向量，保证最终顺序一致
        Map<Integer, float[]> resultVectorMap = Maps.newHashMap();
        // 未命中缓存的文本：key 为原始下标，value 为文本，LinkedHashMap 保证插入顺序以匹配 API 返回顺序
        Map<Integer, String> missedMap = Maps.newLinkedHashMap();

        // 逐条查缓存，区分命中与未命中
        for (int i = 0; i < textList.size(); i++) {
            String cachedKey = CACHE_PREFIX + DigestUtil.md5Hex(textList.get(i));
            String cachedValue = redisTemplate.opsForValue().get(cachedKey);
            // 若命中缓存
            if (Objects.nonNull(cachedValue)) {
                resultVectorMap.put(i, Convert.convert(float[].class, cachedValue));
            }
            // 若未命中缓存
            else {
                missedMap.put(i, textList.get(i));
            }
        }

        log.debug("EmbeddingService.embedBatch totalSize={},resultVectorMapSize={},missedSize={}", textList.size(), resultVectorMap.size(), missedMap.size());

        // 存在未命中的文本，调 API 获取向量并回写缓存
        if (MapUtils.isNotEmpty(missedMap)) {
            // 通过 AopContext.currentProxy() 获取代理对象调用, 确保 @Retryable / @Recover 生效
            List<float[]> newVectorList = ((EmbeddingService) AopContext.currentProxy()).embedFromApi(missedMap.values());

            // 将 API 返回的向量按原始下标回填，并写入缓存
            int idx = 0;
            for (Map.Entry<Integer, String> entry : missedMap.entrySet()) {
                float[] vector = newVectorList.get(idx++);
                resultVectorMap.put(entry.getKey(), vector);

                // 回写缓存，TTL 由配置控制
                String cacheKey = CACHE_PREFIX + DigestUtil.md5Hex(entry.getValue());
                redisTemplate.opsForValue().set(cacheKey, ArrayUtil.join(vector, ","), ragCacheProperties.getEmbeddingTtl());
            }
        }

        // 按原始输入顺序组装最终结果
        return IntStream.range(0, textList.size())
                .mapToObj(resultVectorMap::get)
                .toList();
    }

    /**
     * 调 Embedding API，按批次处理，避免单次请求过大
     * 带重试机制：网络抖动时自动重试 3 次，指数退避（1s → 2s）
     *
     * @param texts 待向量化的文本列表
     * @return 与输入顺序对应的向量列表
     */
    @Retryable(retryFor = {ResourceAccessException.class,        // 网络 IO 异常(连接超时/读超时/Connection reset)
            HttpServerErrorException.class,        // 5xx 服务端错误(502/503/504)
            TimeoutException.class},               // 超时异常
            noRetryFor = HttpClientErrorException.class,   // 4xx 不重试(401/400/413), 重试结果一样纯浪费 Token
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2))
    public List<float[]> embedFromApi(Collection<String> texts) {
        // 收集所有批次的向量结果
        List<float[]> resultList = Lists.newArrayList();
        // 累计 Token 消耗，用于成本监控
        AtomicInteger totalTokens = new AtomicInteger(0);

        List<List<String>> batchGroupList = CollectionUtil.split(texts, BATCH_SIZE);
        for (int i = 0; i < batchGroupList.size(); i++) {
            List<String> batchList = batchGroupList.get(i);

            // 记录批次耗时，用于性能监控
            long batchStart = System.currentTimeMillis();
            EmbeddingResponse embeddingResponse = embeddingModel.call(new EmbeddingRequest(batchList, null));
            long elapsed = System.currentTimeMillis() - batchStart;

            // 统计 Token 消耗（用于成本监控）
            totalTokens.addAndGet(embeddingResponse.getMetadata().getUsage().getTotalTokens());

            // 按 index 排序后提取向量，确保与输入顺序一致
            // Spring AI 1.1.x：Embedding.getOutput() 返回 float[]
            List<Embedding> embeddingList = embeddingResponse.getResults().stream()
                    .sorted(Comparator.comparingInt(Embedding::getIndex))
                    .toList();
            resultList.addAll(CollStreamUtil.toList(embeddingList, Embedding::getOutput));

            log.info("EmbeddingService.embedFromApi batchNo={}/{},batchSize={},elapsed={}", i + 1, batchGroupList.size(), batchList.size(), elapsed);
        }
        log.info("EmbeddingService.embedFromApi size={},totalTokens={}", texts.size(), totalTokens.get());

        return resultList;
    }

    /**
     * 重试耗尽后的兜底方法
     * 当 embedFromApi 重试达到最大次数仍失败时触发，记录错误日志并抛出异常
     *
     * @param e     最后一次重试抛出的异常
     * @param texts 触发失败的文本列表
     */
    @Recover
    public List<float[]> embedFromApiFallback(Exception e, Collection<String> texts) {
        log.error("[Embedding] 重试3次后仍失败,texts.size={},error={}", texts.size(), e.getMessage());
        throw new RuntimeException("Embedding API 调用失败，已重试3次：" + e.getMessage(), e);
    }
}