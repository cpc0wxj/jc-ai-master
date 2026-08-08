package com.jichi.ragkb.service.handler.parse;

import com.jichi.ragkb.dto.ParseResult;
import com.jichi.ragkb.enums.SupportedFileType;
import com.jichi.ragkb.service.manager.parse.DocumentParseHandler;
import lombok.SneakyThrows;
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

/**
 * Markdown (MD) 文件解析处理器
 * 负责解析 Markdown 文件内容，按一级/二级标题分节并提取纯文本
 */
@Slf4j
@Component
public class MarkdownParseHandler implements DocumentParseHandler {
    /**
     * Markdown 标题正则模式
     * 匹配一至三级标题，如：# 标题、## 标题、### 标题
     * 捕获组用于提取标题文本
     */
    private static final Pattern HEADING_PATTERN = Pattern.compile("^#{1,3}\\s+(.+)");

    /**
     * 获取支持的文件类型
     */
    @Override
    public SupportedFileType getSupportedFileType() {
        return SupportedFileType.MD;
    }

    /**
     * 解析 Markdown 文件内容
     */
    @Override
    @SneakyThrows
    public ParseResult parse(String fileName, InputStream inputStream) {
        List<ParseResult.PageContent> pageContentList = Lists.newArrayList();

        // 读取完整的 Markdown 内容为字符串
        String markdown = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        String[] lines = markdown.split("\n");

        // 当前节的标题（如果有）
        String currentTitle = null;
        // 当前节的累积内容
        StringBuilder currentSection = new StringBuilder();
        // 已保存的节数量计数器，初始值为 1
        int sectionCount = 1;
        // 是否在代码块内，避免代码块里的 # 注释被误识别为标题
        boolean inCodeBlock = false;

        for (String line : lines) {
            // 若为代码块边界
            if (line.startsWith("```")) {
                // 切换代码块状态
                inCodeBlock = !inCodeBlock;
            }
            // 代码块内部或边界行：原样保留内容，不识别其中的标题
            // 注意：闭合的 ``` 会将 inCodeBlock 切换为 false，但仍需保留该行
            if (inCodeBlock || line.startsWith("```")) {
                currentSection.append(line).append("\n");
                continue;
            }

            // 匹配标题行
            Matcher matcher = HEADING_PATTERN.matcher(line);
            if (matcher.matches() && (line.startsWith("# ") || line.startsWith("## "))) {
                // 若当前节已有足够内容 (>100 字符)，则保存当前节并开始新节
                if (currentSection.length() > 100) {
                    // 创建新的节内容对象并添加到列表中
                    ParseResult.PageContent pageContent = new ParseResult.PageContent()
                            // 使用后缀递增 (sectionCount++)，先传递当前节号给 setPageNum，然后再递增
                            .setPageNum(sectionCount++)
                            // 去除 Markdown 语法后设置节文本
                            .setText(stripMarkdownSyntax(currentSection.toString()))
                            // 设置节的标题
                            .setSectionTitle(currentTitle);
                    pageContentList.add(pageContent);

                    // 重置状态，开始新的节
                    currentSection = new StringBuilder();
                }

                // 更新当前节的标题为新的标题
                currentTitle = matcher.group(1);
            }

            // 将当前行添加到当前节内容中
            currentSection.append(line).append("\n");
        }

        // 循环结束后，保存最后一个未保存的节
        if (StringUtils.isNotEmpty(currentSection)) {
            ParseResult.PageContent pageContent = new ParseResult.PageContent()
                    .setPageNum(sectionCount)
                    .setText(stripMarkdownSyntax(currentSection.toString()))
                    .setSectionTitle(currentTitle);
            pageContentList.add(pageContent);
        }

        // 若无有效页面 (文档没有标题或内容不足)，整体作为一页
        if (CollectionUtils.isEmpty(pageContentList)) {
            ParseResult.PageContent pageContent = new ParseResult.PageContent()
                    .setPageNum(1)
                    .setText(stripMarkdownSyntax(markdown));
            pageContentList.add(pageContent);
        }

        log.info("MarkdownParseHandler.parse fileName={},pageSize={}", fileName, pageContentList.size());

        return new ParseResult()
                .setSuccess(true)
                .setPageContentList(pageContentList)  // 所有节的内容列表
                .setTotalPageNum(pageContentList.size());  // 总节数
    }

    /**
     * 去除 Markdown 语法符号，提取纯文本
     */
    private String stripMarkdownSyntax(String markdown) {
        return markdown
                // 代码块替换为标记
                .replaceAll("```[\\s\\S]*?```", " [代码块] ")
                // 行内代码去掉反引号
                .replaceAll("`([^`]+)`", "$1")
                // 图片替换为标记
                .replaceAll("!\\[.*?\\]\\(.*?\\)", " [图片] ")
                // 链接保留文字
                .replaceAll("\\[([^\\]]+)\\]\\(.*?\\)", "$1")
                // 标题符号（多行模式：逐行）
                .replaceAll("(?m)^#{1,6}\\s+", "")
                // 加粗符号
                .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")
                // 斜体符号
                .replaceAll("\\*([^*]+)\\*", "$1")
                // 无序列表标记（多行模式：逐行）
                .replaceAll("(?m)^[-*+]\\s+", "")
                // 有序列表标记（多行模式：逐行）
                .replaceAll("(?m)^\\d+\\.\\s+", "")
                // 合并连续空行
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }
}