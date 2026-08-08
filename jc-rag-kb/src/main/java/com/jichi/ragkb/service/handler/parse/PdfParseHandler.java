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

@Slf4j
@Component
public class PdfParseHandler implements DocumentParseHandler {
    /**
     * 识别章节标题：以"第X章"/"第X节"/"一、"/"1."等开头的行
     */
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(第[一二三四五六七八九十百\\d]+[章节]|[一二三四五六七八九十]+、|\\d+\\.)\\s*.+");

    @Override
    public SupportedFileType getSupportedFileType() {
        return SupportedFileType.PDF;
    }

    @Override
    @SneakyThrows
    public ParseResult parse(String fileName, InputStream inputStream) {
        try (PDDocument pdDocument = Loader.loadPDF(inputStream.readAllBytes())) {
            List<ParseResult.PageContent> pageContentList = Lists.newArrayList();
            for (int pageNum = 1; pageNum <= pdDocument.getNumberOfPages(); pageNum++) {
                try {
                    PDFTextStripper pdfTextStripper = new PDFTextStripper();
                    pdfTextStripper.setSortByPosition(true);  // 按位置排序，处理多栏布局
                    pdfTextStripper.setStartPage(pageNum);
                    pdfTextStripper.setEndPage(pageNum);
                    String text = pdfTextStripper.getText(pdDocument);

                    text = cleanText(text);

                    if (StringUtils.isBlank(text)) {
                        log.info("PdfParseHandler.parse 解析内容为空 pageNum={}", pageNum);
                        continue;
                    }

                    ParseResult.PageContent pageContent = new ParseResult.PageContent()
                            .setPageNum(pageNum)
                            .setText(text)
                            .setSectionTitle(detectHeading(text));
                    pageContentList.add(pageContent);
                } catch (Exception e) {
                    // 单页解析失败不影响其他页
                    log.warn("PdfParseHandler.parse 解析失败 pageNum={},message={}", pageNum, e.getMessage());
                }
            }

            log.info("PdfParseHandler.parse fileName={},pageNum={},pageSize={}", fileName, pdDocument.getNumberOfPages(), pageContentList.size());

            if (CollectionUtils.isEmpty(pageContentList)) {
                return ParseResult.failure("PDF 解析后无有效文本内容，可能是纯图片 PDF，需要 OCR 处理");
            }

            return new ParseResult()
                    .setSuccess(true)
                    .setPageContentList(pageContentList)
                    .setTotalPageNum(pdDocument.getNumberOfPages())
                    .setTitle(extractTitle(pageContentList));
        }
    }

    /**
     * 清理 PDF 解析出的文本：去除多余空白、修复换行
     */
    private String cleanText(String raw) {
        if (Objects.isNull(raw)) {
            return "";
        }

        return raw.replaceAll("\\r\\n", "\n")
                .replaceAll("\\r", "\n")
                // 多个空格合并
                .replaceAll("[ \\t]+", " ")
                // 多个空行合并为两个
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }

    /**
     * 从文本开头几行识别章节标题
     */
    private String detectHeading(String text) {
        String[] lines = text.split("\n");
        for (int i = 0; i < Math.min(3, lines.length); i++) {
            String line = lines[i].strip();
            if (line.length() > 2 && line.length() < 50) {
                Matcher m = HEADING_PATTERN.matcher(line);
                if (m.matches()) {
                    return line;
                }
            }
        }
        return null;
    }

    /**
     * 取第一页文本的第一行作为文档标题
     */
    private String extractTitle(List<ParseResult.PageContent> pageContentList) {
        if (CollectionUtils.isEmpty(pageContentList)) {
            return null;
        }

        ParseResult.PageContent pageContent = CollUtil.getFirst(pageContentList);
        String[] lines = pageContent.getText().split("\n");
        for (String line : lines) {
            line = line.strip();
            if (StringUtils.isNotBlank(line) && line.length() < 100) {
                return line;
            }
        }
        return null;
    }
}