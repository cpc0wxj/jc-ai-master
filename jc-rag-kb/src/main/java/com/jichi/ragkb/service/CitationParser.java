package com.jichi.ragkb.service;

import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 引用标注解析器
 * 负责解析模型回答中的引用标注（来源：[参考N]），提取被引用的 chunk 索引，并支持清理引用标记得到纯净文本
 */
@Slf4j
@Component
public class CitationParser {
    /**
     * 引用标注匹配模式
     * 匹配（来源：[参考1][参考2]）或 [参考1] 等格式
     * 捕获组 1 用于提取参考编号
     */
    private static final Pattern CITATION_PATTERN = Pattern.compile("(?:（来源：|\\[)参考(\\d+)(?:）|\\])");

    /**
     * 从回答中提取全部引用编号
     * 按首次出现顺序去重收集，供 SourceBuilder 关联具体 chunk
     *
     * @param answer 模型回答文本
     * @return 被引用的 chunk 索引集合（1-based，按首次出现顺序排列）
     */
    public Set<Integer> extractCitedIndices(String answer) {
        // 使用 LinkedHashSet 保持引用出现顺序并自动去重
        Set<Integer> resultSet = Sets.newLinkedHashSet();
        Matcher matcher = CITATION_PATTERN.matcher(answer);
        // 逐个匹配回答中的引用标注
        while (matcher.find()) {
            // 解析捕获组中的参考编号并收集
            resultSet.add(Integer.parseInt(matcher.group(1)));
        }
        return resultSet;
    }

    /**
     * 清理回答文本中的引用标记
     * 依次移除完整来源括号与残留的裸引用标记，并压缩多余空白
     *
     * @param answer 模型回答文本
     * @return 移除引用标记后的纯净文本
     */
    public String cleanCitations(String answer) {
        return answer
                // 移除完整的来源括号（来源：[参考1][参考2]）
                .replaceAll("（来源：(?:\\[参考\\d+\\])+）", "")
                // 移除残留的裸引用标记 [参考1]
                .replaceAll("\\[参考\\d+\\]", "")
                // 将连续空白压缩为单个空格
                .replaceAll("\\s+", " ")
                .strip();
    }
}