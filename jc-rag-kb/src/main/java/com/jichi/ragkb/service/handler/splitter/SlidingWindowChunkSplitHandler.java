package com.jichi.ragkb.service.handler.splitter;

import com.jichi.ragkb.config.RagChunkProperties;
import com.jichi.ragkb.dto.ChunkResult;
import com.jichi.ragkb.dto.ParseResult;
import com.jichi.ragkb.enums.ChunkSplitStrategy;
import com.jichi.ragkb.service.manager.splitter.ChunkSplitHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 固定窗口滑动分块
 * 核心逻辑
 * 1. 将文档所有页的文本合并成一个大字符串
 * 2. 按 chunkSize 滑动，步长 = chunkSize - chunkOverlap
 * 3. 尽量在句子/段落边界处断开，避免在句子中间截断
 */
@Slf4j
@Component
public class SlidingWindowChunkSplitHandler implements ChunkSplitHandler {
    /**
     * 获取分块策略类型
     */
    @Override
    public ChunkSplitStrategy getChunkSplitStrategy() {
        return ChunkSplitStrategy.SLIDING_WINDOW;
    }

    /**
     * 滑动窗口分块入口
     * 逐页切分文本，并将切分结果按全局顺序编号汇总
     */
    @Override
    public List<ChunkResult> split(ParseResult parseResult, RagChunkProperties ragChunkProperties) {
        List<ChunkResult> chunkResultList = Lists.newArrayList();

        // 全局分块序号，从 0 开始递增
        int chunkIndex = 0;

        // 逐页处理，每页独立切分，保留页码与节标题来源信息
        for (ParseResult.PageContent pageContent : parseResult.getPageContentList()) {
            // 若为空内容
            if (StringUtils.isBlank(pageContent.getText())) {
                continue;
            }

            // 按配置的窗口大小与重叠量对当前页文本进行滑动切分
            List<String> pageChunkList = splitText(pageContent.getText(), ragChunkProperties.getSize(), ragChunkProperties.getOverlap());

            for (String chunkText : pageChunkList) {
                // 若为空内容
                if (StringUtils.isBlank(chunkText)) {
                    continue;
                }

                // 构建分块结果，记录序号、内容、来源页码、节标题及估算 Token 数
                Integer estimatedTokens = estimateTokens(chunkText);
                ChunkResult chunkResult = new ChunkResult()
                        .setChunkIndex(chunkIndex++)
                        .setContent(chunkText)
                        .setPageNum(pageContent.getPageNum())
                        .setSectionTitle(pageContent.getSectionTitle())
                        .setEstimatedTokens(estimatedTokens);
                chunkResultList.add(chunkResult);
            }
        }

        return chunkResultList;
    }

    /**
     * 核心分块逻辑，在句子边界处断开
     *
     * @param text      待切分的文本
     * @param chunkSize 单块最大字符数
     * @param overlap   相邻块之间的重叠字符数
     * @return 切分后的文本块列表
     */
    private List<String> splitText(String text, int chunkSize, int overlap) {
        List<String> result = Lists.newArrayList();
        // 当前块的起始位置
        int start = 0;

        while (start < text.length()) {
            // 计算当前块的结束位置，不超过文本总长度
            int end = Math.min(start + chunkSize, text.length());

            // 如果没有到文本末尾，尝试在句子/段落边界处断开
            if (end < text.length()) {
                end = findGoodBreakPoint(text, end, overlap);
            }

            // 截取并去除首尾空白
            String chunk = text.substring(start, end).strip();
            if (StringUtils.isNotBlank(chunk)) {
                result.add(chunk);
            }

            // 下一块起始位置回退 overlap，制造重叠区域
            int nextStart = end - overlap;
            // 防止重叠过大导致起始位置不前进，陷入死循环
            if (nextStart <= start) {
                nextStart = end;
            }
            start = nextStart;
        }

        return result;
    }

    /**
     * 从 position 向前找到一个好的断点（段落 > 句号 > 逗号 > 空格）
     * 最多回退 100 字符，找不到就直接断
     *
     * @param text     待切分的文本
     * @param position 理想结束位置（向前搜索的起点）
     * @param overlap  重叠字符数，用于约束最小回退范围
     * @return 实际断点位置
     */
    private int findGoodBreakPoint(String text, int position, int overlap) {
        // 搜索范围：最多回退 100 字符，且不能小于 overlap，避免回退过多
        int searchRange = Math.min(100, position - overlap);

        // 优先级：段落换行 > 句号/问号/感叹号 > 分号/逗号 > 空格
        String[] breakChars = {"\n\n", "\n", "。", "！", "？", "；", "，", " "};

        // 按优先级依次查找最近的分隔符
        for (String breakChar : breakChars) {
            int idx = text.lastIndexOf(breakChar, position);
            // 命中且在允许的回退范围内，返回分隔符之后的位置作为断点
            if (idx > position - searchRange && idx > 0) {
                return idx + breakChar.length();
            }
        }

        // 找不到好的断点，直接截断
        return position;
    }

    /**
     * 简单的 Token 估算：中文每字约 1.5 Token，英文每字符约 0.3 Token
     * 不依赖外部 Tokenizer，近似计算。
     *
     * @param text 待估算的文本
     * @return 估算的 Token 数量
     */
    private Integer estimateTokens(String text) {
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