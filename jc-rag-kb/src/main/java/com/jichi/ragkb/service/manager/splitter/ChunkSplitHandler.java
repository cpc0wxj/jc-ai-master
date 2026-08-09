package com.jichi.ragkb.service.manager.splitter;

import com.jichi.ragkb.config.ChunkConfig;
import com.jichi.ragkb.dto.ParseResult;
import com.jichi.ragkb.dto.ChunkResult;
import com.jichi.ragkb.enums.ChunkSplitStrategy;

import java.util.List;

public interface ChunkSplitHandler {
    /**
     * 分块策略类型
     */
    ChunkSplitStrategy getChunkSplitStrategy();

    /**
     * 将解析结果拆分为若干块。
     *
     * @param parseResult 文档解析结果（含多页内容）
     * @param config      分块参数
     * @return 分块列表
     */
    List<ChunkResult> split(ParseResult parseResult, ChunkConfig config);
}