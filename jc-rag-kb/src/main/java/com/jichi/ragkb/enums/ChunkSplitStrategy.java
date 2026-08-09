package com.jichi.ragkb.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文档分块策略类型枚举，标识不同的分块策略。
 */
@Getter
@AllArgsConstructor
public enum ChunkSplitStrategy {
    SLIDING_WINDOW,
    STRUCTURE_AWARE;
}