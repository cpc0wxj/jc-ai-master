package com.jichi.ragkb.service.manager.splitter;

import com.jichi.ragkb.config.RagChunkProperties;
import com.jichi.ragkb.dto.ChunkResult;
import com.jichi.ragkb.dto.ParseResult;
import com.jichi.ragkb.enums.ChunkSplitStrategy;

import java.util.List;
import java.util.Objects;

public interface ChunkSplitHandler {
    /**
     * 分块策略类型
     */
    ChunkSplitStrategy getChunkSplitStrategy();

    /**
     * 将解析结果拆分为若干块。
     *
     * @param parseResult        文档解析结果（含多页内容）
     * @param ragChunkProperties 分块参数
     * @return 分块列表
     */
    List<ChunkResult> split(ParseResult parseResult, RagChunkProperties ragChunkProperties);

    /**
     * 简单的 Token 估算：中文每字约 1.5 Token，英文每字符约 0.3 Token
     * 不依赖外部 Tokenizer，近似计算
     *
     * @param text 待估算的文本
     * @return 估算的 Token 数量
     */
    static Integer estimateTokens(String text) {
        if (Objects.isNull(text)) {
            return 0;
        }

        // 中文字符计数
        int chineseChars = 0;
        // 其他非空白字符计数
        int otherChars = 0;
        for (char c : text.toCharArray()) {
            // 判定为 CJK 统一汉字（U+4E00 ~ U+9FFF）
            if (c >= '\u4e00' && c <= '\u9fff') {
                chineseChars++;
            } else if (!Character.isWhitespace(c)) {
                otherChars++;
            }
        }
        // 按权重加权求和
        return (int) (chineseChars * 1.5 + otherChars * 0.3);
    }
}