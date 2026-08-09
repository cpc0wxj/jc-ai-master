package com.jichi.ragkb.service.manager.splitter;

import com.jichi.ragkb.config.ChunkConfig;
import com.jichi.ragkb.dto.ChunkResult;
import com.jichi.ragkb.dto.ParseResult;
import com.jichi.ragkb.service.handler.splitter.SlidingWindowChunkSplitter;
import com.jichi.ragkb.service.handler.splitter.StructureAwareChunkSplitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkService {
    private final SlidingWindowChunkSplitter slidingWindowSplitter;
    private final StructureAwareChunkSplitter structureAwareSplitter;

    @Value("${rag.chunk.size:512}")
    private int defaultChunkSize;

    @Value("${rag.chunk.overlap:64}")
    private int defaultOverlap;

    /**
     * 对解析结果进行分块。
     * 如果文档有清晰的章节结构，使用结构感知分块；否则使用固定窗口分块。
     */
    public List<ChunkResult> chunk(ParseResult parseResult) {
        ChunkConfig chunkConfig = new ChunkConfig()
                .setChunkSize(defaultChunkSize)
                .setChunkOverlap(defaultOverlap);

        return chunk(parseResult, chunkConfig);
    }

    public List<ChunkResult> chunk(ParseResult parseResult, ChunkConfig config) {
        if (parseResult == null || !parseResult.isSuccess()) {
            return List.of();
        }

        // 判断是否应该用结构感知分块：文档有明显标题结构
        boolean hasStructure = parseResult.getPageContentList().stream().anyMatch(temp -> Objects.nonNull(temp.getSectionTitle()));

        ChunkSplitter splitter = hasStructure && config.isStructureAware()
                ? structureAwareSplitter
                : slidingWindowSplitter;

        List<ChunkResult> chunks = splitter.split(parseResult, config);

        // 过滤掉太短的块（少于 20 字符的碎片没有检索价值）
        chunks = chunks.stream()
                .filter(c -> c.getContent().length() >= 20)
                .toList();

        log.info("[分块] 完成分块：策略={}，共{}块，总字符={}",
                splitter.getClass().getSimpleName(),
                chunks.size(),
                chunks.stream().mapToInt(c -> c.getContent().length()).sum());

        return chunks;
    }
}