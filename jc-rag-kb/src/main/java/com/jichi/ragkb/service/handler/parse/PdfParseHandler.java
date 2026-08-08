package com.jichi.ragkb.service.handler.parse;

import cn.hutool.core.collection.CollUtil;
import com.google.common.collect.Lists;
import com.jichi.ragkb.dto.ParseResult;
import com.jichi.ragkb.enums.SupportedFileType;
import com.jichi.ragkb.service.manager.parse.DocumentParseHandler;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PDF 文件解析处理器
 * 负责解析 PDF 文件内容，提取文本、识别章节标题和文档标题
 */
@Slf4j
@Component
public class PdfParseHandler implements DocumentParseHandler {
    /**
     * 章节标题正则模式
     * 匹配以下格式的章节标题:
     * 示例匹配:
     * 第一章 引言
     * 第二节 背景介绍
     * 一、概述
     * 1. 基本概念
     */
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(第[一二三四五六七八九十百\\d]+[章节]|[一二三四五六七八九十]+、|\\d+\\.)\\s*.+");

    /**
     * 获取支持的文件类型
     *
     * @return SupportedFileType.PDF 表示此处理器专门用于解析 PDF 文件
     */
    @Override
    public SupportedFileType getSupportedFileType() {
        return SupportedFileType.PDF;
    }

    /**
     * 解析 PDF 文件内容
     */
    @Override
    @SneakyThrows
    public ParseResult parse(String fileName, InputStream inputStream) {
        try (PDDocument pdDocument = Loader.loadPDF(inputStream.readAllBytes())) {
            List<ParseResult.PageContent> pageContentList = Lists.newArrayList();
            for (int pageNum = 1; pageNum <= pdDocument.getNumberOfPages(); pageNum++) {
                try {
                    // 创建独立的 PDFTextStripper 实例用于每一页
                    PDFTextStripper pdfTextStripper = new PDFTextStripper();
                    // 设置按位置排序，正确处理多栏布局的文本顺序
                    pdfTextStripper.setSortByPosition(true);
                    // 设置只提取当前页的文本
                    pdfTextStripper.setStartPage(pageNum);
                    pdfTextStripper.setEndPage(pageNum);
                    String text = pdfTextStripper.getText(pdDocument);

                    // 清理文本：统一格式、去除多余空白
                    text = cleanText(text);

                    if (StringUtils.isBlank(text)) {
                        log.info("PdfParseHandler.parse 解析内容为空 pageNum={}", pageNum);
                        continue;
                    }

                    // 构建页面内容对象
                    String sectionTitle = detectHeading(text);
                    ParseResult.PageContent pageContent = new ParseResult.PageContent()
                            .setPageNum(pageNum)
                            .setText(text)
                            .setSectionTitle(sectionTitle);
                    pageContentList.add(pageContent);
                }
                // 单页解析失败不影响其他页面，继续处理后续页面
                catch (Exception e) {
                    log.warn("PdfParseHandler.parse 解析失败 pageNum={},message={}", pageNum, e.getMessage());
                }
            }

            log.info("PdfParseHandler.parse fileName={},pageNum={},pageSize={}", fileName, pdDocument.getNumberOfPages(), pageContentList.size());

            // 若无有效页面
            if (CollectionUtils.isEmpty(pageContentList)) {
                return ParseResult.failure("PDF 解析后无有效文本内容，可能是纯图片 PDF，需要 OCR 处理");
            }

            String title = extractTitle(pageContentList);
            return new ParseResult()
                    .setSuccess(true)
                    .setPageContentList(pageContentList)  // 所有有效页面的内容列表
                    .setTotalPageNum(pdDocument.getNumberOfPages())  // PDF 总页数
                    .setTitle(title);  // 提取文档标题
        }
    }

    /**
     * 清理 PDF 解析出的原始文本
     *
     * @param raw 原始文本
     * @return 清理后的规范化文本
     */
    private String cleanText(String raw) {
        if (Objects.isNull(raw)) {
            return "";  // 空文本返回空字符串
        }

        return raw
                // Windows 换行符转 Unix 风格
                .replaceAll("\\r\\n", "\n")
                // Mac 经典换行符转 Unix 风格
                .replaceAll("\\r", "\n")
                // 多个空格或制表符合并为单个空格
                .replaceAll("[ \\t]+", " ")
                // 连续 3 个或以上空行合并为 2 个空行 (保留段落分隔)
                .replaceAll("\n{3,}", "\n\n")
                .strip();  // 去除首尾空白
    }

    /**
     * 检测文本是否包含章节标题
     * <p>检查策略:</p>
     * <ul>
     *   <li>只检查文本的前 3 行 (章节标题通常在开头附近)</li>
     *   <li>标题长度应在 2-50 字符之间 (过滤太短或过长的内容)</li>
     *   <li>使用正则表达式匹配标准章节格式 (如"第一章"、"一、"等)</li>
     * </ul>
     *
     * @param text 待检测的文本
     * @return 如果检测到章节标题则返回标题文本，否则返回 null
     */
    private String detectHeading(String text) {
        String[] lines = text.split("\n");

        // 遍历前 3 行 或实际行数，取较小值
        for (int i = 0; i < Math.min(3, lines.length); i++) {
            String line = lines[i].strip();
            // 标题长度校验：避免误判
            if (line.length() > 2 && line.length() < 50) {
                // 使用正则匹配章节标题格式
                Matcher matcher = HEADING_PATTERN.matcher(line);
                if (matcher.matches()) {
                    return line;
                }
            }
        }

        return null;
    }

    /**
     * 从解析结果中提取文档标题
     */
    private String extractTitle(List<ParseResult.PageContent> pageContentList) {
        if (CollectionUtils.isEmpty(pageContentList)) {
            return null;  // 没有解析内容，无法提取标题
        }

        // 获取第一页
        ParseResult.PageContent pageContent = CollUtil.getFirst(pageContentList);
        String[] lines = pageContent.getText().split("\n");

        // 遍历每一行
        for (String line : lines) {
            line = line.strip();
            // 找到第一个合理长度的非空行作为标题
            if (StringUtils.isNotBlank(line) && line.length() < 100) {
                return line;
            }
        }

        return null;
    }
}