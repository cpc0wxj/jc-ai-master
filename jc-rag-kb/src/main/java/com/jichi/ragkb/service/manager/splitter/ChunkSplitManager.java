package com.jichi.ragkb.service.manager.splitter;

import com.google.common.collect.Maps;
import com.jichi.ragkb.config.ChunkConfig;
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

    private final ChunkConfig chunkConfig;

    @Override
    public Object postProcessBeforeInitialization(@NonNull Object bean, @NonNull String beanName) throws BeansException {
        if (bean instanceof ChunkSplitHandler handler) {
            if (splitterMap.containsKey(handler.getChunkSplitStrategy())) {
                throw new IllegalStateException("ChunkSplitManager.postProcessBeforeInitialization 重复注册ChunkSplitHandler");
            }
            splitterMap.put(handler.getChunkSplitStrategy(), handler);
            log.info("ChunkSplitManager.postProcessBeforeInitialization 已加载分块器={}", handler.getChunkSplitStrategy());
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

        ChunkSplitHandler splitter = getHandler(parseResult);

        List<ChunkResult> chunks = splitter.split(parseResult, chunkConfig);

        // 过滤掉太短的块（少于 20 字符的碎片没有检索价值）
        chunks = chunks.stream()
                .filter(c -> c.getContent().length() >= 20)
                .toList();

        log.info("[分块] 完成分块：策略={}，共{}块，总字符={}",
                splitter.getChunkSplitStrategy(),
                chunks.size(),
                chunks.stream().mapToInt(c -> c.getContent().length()).sum());

        return chunks;
    }

    /**
     * 根据文档结构选择对应的分块器
     */
    private ChunkSplitHandler getHandler(ParseResult parseResult) {
        // 判断是否应该用结构感知分块：文档有明显标题结构
        boolean hasStructure = parseResult.getPageContentList().stream().anyMatch(temp -> Objects.nonNull(temp.getSectionTitle()));

        ChunkSplitStrategy strategy = hasStructure && chunkConfig.isStructureAware()
                ? ChunkSplitStrategy.STRUCTURE_AWARE
                : ChunkSplitStrategy.SLIDING_WINDOW;

        return splitterMap.get(strategy);
    }
}