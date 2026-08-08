package com.jichi.ragkb.service.handler.parse;

import cn.hutool.core.collection.CollStreamUtil;
import com.google.common.collect.Lists;
import com.jichi.ragkb.dto.ParseResult;
import com.jichi.ragkb.enums.SupportedFileType;
import com.jichi.ragkb.service.manager.parse.DocumentParseHandler;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;

/**
 * Word (DOCX) 文件解析处理器
 * 负责解析 DOCX 文件内容，提取段落、表格文本，按标题样式分节并识别文档标题
 */
@Slf4j
@Component
public class DocxParseHandler implements DocumentParseHandler {
    /**
     * 获取支持的文件类型
     */
    @Override
    public SupportedFileType getSupportedFileType() {
        return SupportedFileType.DOCX;
    }

    /**
     * 解析 Word (DOCX) 文件内容
     */
    @Override
    @SneakyThrows
    public ParseResult parse(String fileName, InputStream inputStream) {
        try (XWPFDocument xwpfDocument = new XWPFDocument(inputStream)) {
            // 按文档实际顺序遍历段落和表格，保证表格归属到正确的节
            List<ParseResult.PageContent> pageContentList = Lists.newArrayList();

            // 当前节的累积内容
            StringBuilder currentSection = new StringBuilder();
            // 当前节的标题（如果有）
            String currentTitle = null;
            // 已保存的节数量计数器，初始值为 1
            int sectionCount = 1;

            for (IBodyElement iBodyElement : xwpfDocument.getBodyElements()) {
                // 若当前元素为段落类型
                if (iBodyElement instanceof XWPFParagraph xwpfParagraph) {
                    String text = xwpfParagraph.getText();
                    if (StringUtils.isBlank(text)) {
                        // 跳过空段落
                        continue;
                    }

                    // 识别标题样式：匹配 Heading 1/2/3 或中文标题样式
                    // 常见样式名：Heading 1, Heading 2, heading 1, 标题 1, 标题 2 等
                    boolean isHeading = Objects.nonNull(xwpfParagraph.getStyle())
                            && (xwpfParagraph.getStyle().startsWith("Heading")
                            || xwpfParagraph.getStyle().startsWith("heading")
                            || xwpfParagraph.getStyle().contains("标题"));

                    // 若当前元素是标题
                    if (isHeading) {
                        // 若当前节已有足够内容 (>200 字符)
                        if (currentSection.length() > 200) {
                            // 创建新节的内容对象
                            ParseResult.PageContent pageContent = new ParseResult.PageContent()
                                    // 使用后缀递增 (sectionCount++)，先传递当前节号给 setPageNum，然后再递增
                                    .setPageNum(sectionCount++)
                                    // 节内容去除首尾空白
                                    .setText(currentSection.toString().strip())
                                    // 设置节的标题
                                    .setSectionTitle(currentTitle);
                            pageContentList.add(pageContent);

                            // 重置状态，开始新的节
                            currentSection = new StringBuilder();
                        }

                        // 更新当前节的标题为新的标题
                        currentTitle = text;
                    }

                    // 将普通段落或标题文本添加到当前节内容中
                    currentSection.append(text).append("\n");
                }
                // 若当前元素为表格类型
                else if (iBodyElement instanceof XWPFTable xwpfTable) {
                    // 处理表格元素：将表格转换为文本格式附加到当前节
                    // 这样可以保持表格与其上下文内容的关联性
                    StringBuilder tableText = new StringBuilder();

                    // 遍历表格行
                    for (XWPFTableRow xwpfTableRow : xwpfTable.getRows()) {
                        // 提取单元格文本，过滤空白单元格
                        List<String> cellTextList = CollStreamUtil.toList(xwpfTableRow.getTableCells(), xwpfTableCell -> StringUtils.isNotBlank(xwpfTableCell.getText()) ? xwpfTableCell.getText() : null);

                        if (CollectionUtils.isNotEmpty(cellTextList)) {
                            // 将同一行的单元格用" | "连接，每行末尾加换行符
                            tableText.append(String.join(" | ", cellTextList)).append("\n");
                        }
                    }

                    // 如果表格有有效内容
                    if (StringUtils.isNotBlank(tableText)) {
                        // 使用"\n[表格]\n"作为视觉标记，便于后续处理或展示
                        currentSection.append("\n[表格]\n").append(tableText);
                    }
                }
            }

            // 循环结束后，保存最后一个未保存的节
            if (StringUtils.isNotBlank(currentSection)) {
                ParseResult.PageContent pageContent = new ParseResult.PageContent()
                        .setPageNum(sectionCount)
                        .setText(currentSection.toString().strip())
                        .setSectionTitle(currentTitle);
                pageContentList.add(pageContent);
            }

            // 若无有效页面
            if (pageContentList.isEmpty()) {
                return ParseResult.failure("Word 文档内容为空");
            }

            // 记录解析成功日志，包括文件名和分的节数
            log.info("DocxParseHandler.parse fileName={},pageSize={}", fileName, pageContentList.size());

            // 从 Word 文档的核心属性中获取文档标题元数据
            String title;
            try (XWPFWordExtractor xwpfWordExtractor = new XWPFWordExtractor(xwpfDocument)) {
                // 读取文档属性中的 Title 字段
                title = xwpfWordExtractor.getCoreProperties().getTitle();
            }
            return new ParseResult()
                    .setSuccess(true)
                    .setPageContentList(pageContentList)
                    .setTotalPageNum(pageContentList.size())
                    .setTitle(title);
        }
    }
}