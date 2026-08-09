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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结构感知分块：优先在标题/段落边界处断开。
 * 适用场景：结构清晰的文档（技术规范、手册等）。
 * 对于流水文字（新闻、小说）效果不如固定窗口。
 * 核心思路：
 * 1. 按标题行切分为若干"节"
 * 2. 节太大则再用固定窗口切分
 * 3. 节太小则与下一节合并（避免碎片化）
 */
@Slf4j
@Component("structureAwareSplitter")
public class StructureAwareChunkSplitHandler implements ChunkSplitHandler {
    /**
     * 获取分块策略类型
     */
    @Override
    public ChunkSplitStrategy getChunkSplitStrategy() {
        return ChunkSplitStrategy.STRUCTURE_AWARE;
    }

    /**
     * 中英文标题行正则模式
     * 匹配以下格式的标题行：
     * - Markdown 标题：# / ## / ### 开头
     * - 中文章节：第一章 / 第二节 等
     * - 中文序号：一、 二、 等
     * - 数字序号：1. / 1.1 等
     * 捕获组用于提取标题文本（长度限定 2-60 字符）
     */
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,3}\\s+|第[一二三四五六七八九十百\\d]+[章节]|[一二三四五六七八九十]+、|\\d+\\.\\d?\\s+)(.{2,60})$");

    /**
     * 委托的固定窗口分块器，用于对超大节进行二次切分
     */
    private final SlidingWindowChunkSplitHandler slidingSplitter = new SlidingWindowChunkSplitHandler();

    /**
     * 结构感知分块入口
     * 先按标题边界抽取节，再按节大小决定直接成块或降级固定窗口切分
     */
    @Override
    public List<ChunkResult> split(ParseResult parseResult, RagChunkProperties ragChunkProperties) {
        // 按标题边界抽取所有节
        List<TextSection> textSectionList = extractSections(parseResult);
        List<ChunkResult> chunkResultList = Lists.newArrayList();
        // 全局分块序号，从 0 开始递增
        int chunkIndex = 0;

        for (TextSection section : textSectionList) {
            // 节太小（少于50字符）且下一节不是标题开头，合并（在 extractSections 时处理）
            if (section.text().length() <= ragChunkProperties.getSize()) {
                // 节大小合适，直接作为一块
                Integer estimatedTokens = ChunkSplitHandler.estimateTokens(section.text());
                ChunkResult chunkResult = new ChunkResult()
                        .setChunkIndex(chunkIndex++)
                        .setContent(section.text())
                        .setPageNum(section.pageNum())
                        .setSectionTitle(section.title())
                        .setEstimatedTokens(estimatedTokens);
                chunkResultList.add(chunkResult);
            } else {
                // 节太大，降级到固定窗口切分
                // 将当前节包装成单页 ParseResult，复用滑动窗口分块器
                ParseResult.PageContent pageContent = new ParseResult.PageContent()
                        .setPageNum(section.pageNum())
                        .setText(section.text())
                        .setSectionTitle(section.title());
                ParseResult sectionResult = new ParseResult()
                        .setSuccess(true)
                        .setPageContentList(List.of(pageContent))
                        .setTotalPageNum(1);

                List<ChunkResult> subChunkResultList = slidingSplitter.split(sectionResult, ragChunkProperties);
                // 重新编排序号，并回填缺失的节标题
                for (ChunkResult chunkResult : subChunkResultList) {
                    chunkResult.setChunkIndex(chunkIndex++);
                    if (Objects.isNull(chunkResult.getSectionTitle())) {
                        chunkResult.setSectionTitle(section.title());
                    }
                    chunkResultList.add(chunkResult);
                }
            }
        }

        return chunkResultList;
    }

    /**
     * 按标题边界将文档抽取为若干节
     * 遇到标题行且当前节已有足够内容（>50 字符）时，保存当前节并开启新节
     *
     * @param parseResult 文档解析结果
     * @return 按标题切分的节列表
     */
    private List<TextSection> extractSections(ParseResult parseResult) {
        // 节列表
        List<TextSection> sectionList = Lists.newArrayList();

        // 当前节的累积内容
        StringBuilder current = new StringBuilder();
        // 当前节的标题（如果有）
        String currentTitle = null;
        // 当前节所属页码
        int currentPage = 1;

        for (ParseResult.PageContent page : parseResult.getPageContentList()) {
            String[] lines = page.getText().split("\n");
            for (String line : lines) {
                Matcher matcher = HEADING_PATTERN.matcher(line.strip());
                // 命中标题且当前节已有足够内容，保存当前节并开始新节
                if (matcher.matches() && current.length() > 50) {
                    // 遇到标题且当前节有内容，保存
                    TextSection textSection = new TextSection(currentTitle, current.toString().strip(), currentPage);
                    sectionList.add(textSection);

                    current = new StringBuilder();
                    currentTitle = line.strip();
                    currentPage = page.getPageNum();
                }
                // 当前行追加到当前节内容
                current.append(line).append("\n");
            }
            currentPage = page.getPageNum();
        }

        // 循环结束后，保存最后一个未保存的节
        if (StringUtils.isNotBlank(current)) {
            TextSection textSection = new TextSection(currentTitle, current.toString().strip(), currentPage);
            sectionList.add(textSection);
        }

        return sectionList;
    }

    /**
     * 节数据结构，记录标题、正文及所属页码
     */
    record TextSection(String title, String text, int pageNum) {
    }
}