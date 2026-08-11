package com.jichi.ragkb.service.loader;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class PdfParser implements DocumentParser {

    // 识别章节标题：以"第X章"/"第X节"/"一、"/"1."等开头的行
    private static final Pattern HEADING_PATTERN =
            Pattern.compile("^(第[一二三四五六七八九十百\\d]+[章节]|[一二三四五六七八九十]+、|\\d+\\.)\\s*.+");

    @Override
    public String supportedType() {
        return "PDF";
    }

    @Override
    public ParseResult parse(InputStream inputStream, String fileName) {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            int totalPages = document.getNumberOfPages();
            List<ParseResult.PageContent> pages = new ArrayList<>();

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);  // 按位置排序，处理多栏布局

            for (int pageNum = 1; pageNum <= totalPages; pageNum++) {
                try {
                    stripper.setStartPage(pageNum);
                    stripper.setEndPage(pageNum);
                    String text = stripper.getText(document);
                    text = cleanText(text);

                    if (text.isBlank()) {
                        log.debug("[PDF解析] 第{}页内容为空，可能是图片页，跳过", pageNum);
                        continue;
                    }

                    // 跳过目录页：目录条目（"标题 …… 页码"）会污染检索，且字面命中标题词，挤掉真正的正文
                    if (isTocPage(text)) {
                        log.info("[PDF解析] 第{}页疑似目录页，跳过索引", pageNum);
                        continue;
                    }

                    pages.add(ParseResult.PageContent.builder()
                            .pageNum(pageNum)
                            .text(text)
                            .sectionTitle(detectHeading(text))
                            .build());

                } catch (Exception e) {
                    // 单页解析失败不影响其他页
                    log.warn("[PDF解析] 第{}页解析失败：{}", pageNum, e.getMessage());
                }
            }

            if (pages.isEmpty()) {
                return ParseResult.failure("PDF 解析后无有效文本内容，可能是纯图片 PDF，需要 OCR 处理");
            }

            log.info("[PDF解析] 文件={}，总页数={}，有效页数={}", fileName, totalPages, pages.size());

            return ParseResult.builder()
                    .success(true)
                    .pages(pages)
                    .totalPages(totalPages)
                    .title(extractTitle(pages))
                    .build();

        } catch (Exception e) {
            log.error("[PDF解析] 文件={}，解析失败：{}", fileName, e.getMessage(), e);
            return ParseResult.failure("PDF 解析失败：" + e.getMessage());
        }
    }

    // 目录条目行：以"点/空格引导符 + 页码"结尾，如 "整体对接方案 ............ 5"
    private static final Pattern TOC_LINE_PATTERN =
            Pattern.compile(".*[.\\u2026·•\\-\\s]{4,}\\d{1,4}\\s*$");

    /**
     * 判断是否为目录页：若页内"目录条目行"占比较高，则认为是目录页。
     * 兼顾显式出现"目录 / 目 录 / Contents"标题的情况。
     */
    private boolean isTocPage(String text) {
        String[] lines = text.split("\n");
        int nonBlank = 0;
        int tocLines = 0;
        boolean hasTocHeading = false;

        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty()) continue;
            nonBlank++;

            String compact = line.replace(" ", "");
            if (compact.equals("目录") || compact.equalsIgnoreCase("contents")
                    || compact.equalsIgnoreCase("tableofcontents")) {
                hasTocHeading = true;
            }
            if (TOC_LINE_PATTERN.matcher(line).matches()) {
                tocLines++;
            }
        }

        if (nonBlank == 0) return false;
        double ratio = (double) tocLines / nonBlank;
        // 有"目录"标题且出现目录条目，或目录条目占比超过 40%，判定为目录页
        return (hasTocHeading && tocLines >= 2) || ratio >= 0.4;
    }

    /**
     * 清理 PDF 解析出的文本：去除多余空白、修复换行
     */
    private String cleanText(String raw) {
        if (raw == null) return "";
        return raw
                .replaceAll("\\r\\n", "\n")
                .replaceAll("\\r", "\n")
                .replaceAll("[ \\t]+", " ")          // 多个空格合并
                .replaceAll("\\n{3,}", "\n\n")        // 多个空行合并为两个
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
                if (m.matches()) return line;
            }
        }
        return null;
    }

    /**
     * 取第一页文本的第一行作为文档标题
     */
    private String extractTitle(List<ParseResult.PageContent> pages) {
        if (pages.isEmpty()) return null;
        String firstPageText = pages.get(0).getText();
        String[] lines = firstPageText.split("\n");
        for (String line : lines) {
            line = line.strip();
            if (!line.isBlank() && line.length() < 100) {
                return line;
            }
        }
        return null;
    }
}