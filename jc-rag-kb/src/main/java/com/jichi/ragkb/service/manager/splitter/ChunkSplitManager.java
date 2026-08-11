package com.jichi.ragkb.service.manager.splitter;

import cn.hutool.core.collection.CollStreamUtil;
import com.google.common.collect.Maps;
import com.jichi.ragkb.config.RagChunkProperties;
import com.jichi.ragkb.dto.ChunkResult;
import com.jichi.ragkb.dto.ParseResult;
import com.jichi.ragkb.enums.ChunkSplitStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChunkSplitManager implements BeanPostProcessor {
    private static final Map<ChunkSplitStrategy, ChunkSplitHandler> splitterMap = Maps.newHashMap();

    private final RagChunkProperties ragChunkProperties;

    @Override
    public Object postProcessBeforeInitialization(@NonNull Object bean, @NonNull String beanName) throws BeansException {
        if (bean instanceof ChunkSplitHandler handler) {
            if (splitterMap.containsKey(handler.getChunkSplitStrategy())) {
                throw new IllegalStateException("ChunkSplitManager.postProcessBeforeInitialization 重复注册ChunkSplitHandler");
            }
            splitterMap.put(handler.getChunkSplitStrategy(), handler);
            log.info("ChunkSplitManager.postProcessBeforeInitialization 已加载分块器 chunkSplitStrategy={}", handler.getChunkSplitStrategy());
        }
        return bean;
    }

    /**
     * 对解析结果进行分块。
     * 如果文档有清晰的章节结构，使用结构感知分块；否则使用固定窗口分块。
     */
    public List<ChunkResult> chunk(ParseResult parseResult) {
        Boolean success = Optional.ofNullable(parseResult).map(ParseResult::getSuccess).orElse(null);
        if (!Objects.equals(success, Boolean.TRUE)) {
            return List.of();
        }

        ChunkSplitHandler chunkSplitHandler = getHandler(parseResult);

        List<ChunkResult> chunkResultList = chunkSplitHandler.split(parseResult, ragChunkProperties);

        // 过滤掉太短的块（少于 20 字符的碎片没有检索价值）
        chunkResultList = CollStreamUtil.toList(chunkResultList, chunkResult -> chunkResult.getContent().length() >= 20 ? chunkResult : null);

        log.info("ChunkSplitManager.chunk 分块完成 chunkSplitStrategy={},chunkSize={},charSum={}", chunkSplitHandler.getChunkSplitStrategy(), chunkResultList.size(), chunkResultList.stream().mapToInt(c -> c.getContent().length()).sum());

        return chunkResultList;
    }

    /**
     * 根据文档结构选择对应的分块器
     */
    private ChunkSplitHandler getHandler(ParseResult parseResult) {
        // 判断是否应该用结构感知分块：文档有明显标题结构
        boolean hasStructure = parseResult.getPageContentList().stream().anyMatch(temp -> Objects.nonNull(temp.getSectionTitle()));

        ChunkSplitStrategy chunkSplitStrategy = hasStructure && ragChunkProperties.getStructureAware()
                ? ChunkSplitStrategy.STRUCTURE_AWARE
                : ChunkSplitStrategy.SLIDING_WINDOW;

        return splitterMap.get(chunkSplitStrategy);
    }
}