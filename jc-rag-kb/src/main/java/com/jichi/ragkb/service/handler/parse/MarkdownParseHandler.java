package com.jichi.ragkb.service.handler.parse;

import com.jichi.ragkb.dto.ParseResult;
import com.jichi.ragkb.enums.SupportedFileType;
import com.jichi.ragkb.service.manager.parse.DocumentParseHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class MarkdownParseHandler implements DocumentParseHandler {
    private static final Pattern HEADING_PATTERN = Pattern.compile("^#{1,3}\\s+(.+)");

    @Override
    public SupportedFileType getSupportedFileType() {
        return SupportedFileType.MD;
    }

    @Override
    public ParseResult parse(String fileName, InputStream inputStream) {
        try {
            String markdown = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            List<ParseResult.PageContent> pageContentList = Lists.newArrayList();

            // 按一级/二级标题切分为多个节
            String[] lines = markdown.split("\n");
            String currentTitle = null;
            StringBuilder currentSection = new StringBuilder();
            int sectionCount = 0;
            boolean inCodeBlock = false;  // 跟踪是否在 ``` 代码块内，避免代码块里的 # 注释被误识别为标题

            for (String line : lines) {
                // 代码块边界（``` 或 ```语言）——只切换状态，不参与标题判断
                if (line.startsWith("```")) {
                    inCodeBlock = !inCodeBlock;
                    currentSection
                            .append(line)
                            .append("\n");
                    continue;
                }
                // 代码块内部：原样保留，不识别标题
                if (inCodeBlock) {
                    currentSection
                            .append(line)
                            .append("\n");
                    continue;
                }

                Matcher matcher = HEADING_PATTERN.matcher(line);
                if (matcher.matches()
                        && (line.startsWith("# ")
                        || line.startsWith("## "))) {
                    if (currentSection.length() > 100) {
                        ParseResult.PageContent pageContent = new ParseResult.PageContent()
                                .setPageNum(++sectionCount)
                                .setText(stripMarkdownSyntax(currentSection.toString()))
                                .setSectionTitle(currentTitle);
                        pageContentList.add(pageContent);
                        currentSection = new StringBuilder();
                    }
                    currentTitle = matcher.group(1);
                }
                currentSection
                        .append(line)
                        .append("\n");
            }

            if (StringUtils.isNotEmpty(currentSection)) {
                ParseResult.PageContent pageContent = new ParseResult.PageContent()
                        .setPageNum(++sectionCount)
                        .setText(stripMarkdownSyntax(currentSection.toString()))
                        .setSectionTitle(currentTitle);
                pageContentList.add(pageContent);
            }

            // 如果切分后没有内容（文档没有标题），整体作为一页
            if (CollectionUtils.isEmpty(pageContentList)) {
                ParseResult.PageContent pageContent = new ParseResult.PageContent()
                        .setPageNum(1)
                        .setText(stripMarkdownSyntax(markdown));
                pageContentList.add(pageContent);
            }

            log.info("[MD解析] 文件={}，分节={}节", fileName, pageContentList.size());

            return new ParseResult()
                    .setSuccess(true)
                    .setPageContentList(pageContentList)
                    .setTotalPageNum(pageContentList.size());
        } catch (Exception e) {
            log.error("[MD解析] 文件={}，解析失败：{}", fileName, e.getMessage(), e);
            return ParseResult.failure("Markdown 解析失败：" + e.getMessage());
        }
    }

    /**
     * 去除 Markdown 语法符号，提取纯文本（列表/标题正则用 (?m) 多行模式，逐行匹配）
     */
    private String stripMarkdownSyntax(String markdown) {
        return markdown
                .replaceAll("```[\\s\\S]*?```", " [代码块] ")   // 代码块替换为标记
                .replaceAll("`([^`]+)`", "$1")                   // 行内代码去掉反引号
                .replaceAll("!\\[.*?\\]\\(.*?\\)", " [图片] ")   // 图片
                .replaceAll("\\[([^\\]]+)\\]\\(.*?\\)", "$1")    // 链接保留文字
                .replaceAll("(?m)^#{1,6}\\s+", "")               // 标题符号（多行模式：逐行）
                .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")         // 加粗
                .replaceAll("\\*([^*]+)\\*", "$1")               // 斜体
                .replaceAll("(?m)^[-*+]\\s+", "")                // 无序列表（多行模式：逐行）
                .replaceAll("(?m)^\\d+\\.\\s+", "")              // 有序列表（多行模式：逐行）
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }
}