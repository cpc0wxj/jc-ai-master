package com.jichi.ragkb.service.handler.parse;

import com.google.common.collect.Lists;
import com.jichi.ragkb.service.manager.parse.DocumentParseHandler;
import com.jichi.ragkb.enums.SupportedFileType;
import com.jichi.ragkb.dto.ParseResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
public class DocxParseHandler implements DocumentParseHandler {
    @Override
    public SupportedFileType getSupportedFileType() {
        return SupportedFileType.DOCX;
    }

    @Override
    public ParseResult parse(InputStream inputStream, String fileName) {
        try (XWPFDocument xwpfDocument = new XWPFDocument(inputStream)) {
            String currentTitle = null;
            StringBuilder currentSection = new StringBuilder();
            int sectionCount = 0;

            // 按文档实际顺序遍历段落和表格，保证表格归属到正确的节
            List<ParseResult.PageContent> pageContentList = Lists.newArrayList();
            for (IBodyElement iBodyElement : xwpfDocument.getBodyElements()) {
                if (iBodyElement instanceof XWPFParagraph xwpfParagraph) {
                    String text = xwpfParagraph.getText();
                    if (StringUtils.isBlank(text)) {
                        continue;
                    }

                    // 识别标题样式（Heading 1, 2, 3）
                    boolean isHeading = Objects.nonNull(xwpfParagraph.getStyle())
                            && (xwpfParagraph.getStyle().startsWith("Heading")
                            || xwpfParagraph.getStyle().startsWith("heading")
                            || xwpfParagraph.getStyle().contains("标题"));

                    // 遇到新标题且当前节有内容，保存当前节
                    if (isHeading && currentSection.length() > 200) {
                        ParseResult.PageContent pageContent = new ParseResult.PageContent()
                                .setPageNum(++sectionCount)
                                .setText(currentSection.toString().strip())
                                .setSectionTitle(currentTitle);
                        pageContentList.add(pageContent);
                        currentSection = new StringBuilder();
                        currentTitle = text;
                    } else if (isHeading) {
                        currentTitle = text;
                    }

                    currentSection.append(text).append("\n");
                } else if (iBodyElement instanceof XWPFTable xwpfTable) {
                    // 表格 append 到当前节，按文档原始位置归属
                    StringBuilder tableText = new StringBuilder();
                    for (XWPFTableRow xwpfTableRow : xwpfTable.getRows()) {
                        List<String> cellTexts = xwpfTableRow.getTableCells().stream()
                                .map(XWPFTableCell::getText)
                                .filter(t -> !t.isBlank())
                                .toList();
                        if (!cellTexts.isEmpty()) {
                            tableText.append(String.join(" | ", cellTexts)).append("\n");
                        }
                    }
                    if (!tableText.isEmpty()) {
                        currentSection.append("\n[表格]\n").append(tableText);
                    }
                }
            }

            // 保存最后一节
            if (!currentSection.isEmpty()) {
                ParseResult.PageContent pageContent = new ParseResult.PageContent()
                        .setPageNum(++sectionCount)
                        .setText(currentSection.toString().strip())
                        .setSectionTitle(currentTitle);
                pageContentList.add(pageContent);
            }

            if (pageContentList.isEmpty()) {
                return ParseResult.failure("Word 文档内容为空");
            }

            // 提取文档属性中的标题（用 try-with-resources 释放资源）
            String title = null;
            try (XWPFWordExtractor extractor = new XWPFWordExtractor(xwpfDocument)) {
                title = extractor.getCoreProperties().getTitle();
            } catch (Exception ignored) {
            }

            log.info("[DOCX解析] 文件={}，段落分节={}节", fileName, pageContentList.size());

            return new ParseResult()
                    .setSuccess(true)
                    .setPageContentList(pageContentList)
                    .setTotalPageNum(pageContentList.size())
                    .setTitle(title);
        } catch (Exception e) {
            log.error("[DOCX解析] 文件={}，解析失败：{}", fileName, e.getMessage(), e);
            return ParseResult.failure("Word 文档解析失败：" + e.getMessage());
        }
    }
}