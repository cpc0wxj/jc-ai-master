package com.jichi.ragkb.service.handler.parse;

import com.jichi.ragkb.dto.ParseResult;
import com.jichi.ragkb.enums.SupportedFileType;
import com.jichi.ragkb.service.manager.parse.DocumentParseHandler;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
public class TxtParseHandler implements DocumentParseHandler {
    /**
     * UTF-8 解码后若替换字符（U+FFFD）占比超过阈值，则判定为非 UTF-8，降级到 GBK
     */
    private static final double UTF8_DECODE_FAIL_THRESHOLD = 0.01;
    /**
     * 非可打印控制字符占比阈值——超过即判定为二进制文件
     */
    private static final double BINARY_THRESHOLD = 0.05;

    @Override
    public SupportedFileType getSupportedFileType() {
        return SupportedFileType.TXT;
    }

    @SneakyThrows
    @Override
    public ParseResult parse(String fileName, InputStream inputStream) {
        byte[] bytes = inputStream.readAllBytes();

        // 先尝试 UTF-8，乱码率高则降级到 GBK（覆盖国内常见的 ANSI/GBK 文件）
        Charset charset = StandardCharsets.UTF_8;
        String text = new String(bytes, charset);
        long replacementCount = text.chars().filter(c -> Objects.equals(c, 0xFFFD)).count();
        if (replacementCount > text.length() * UTF8_DECODE_FAIL_THRESHOLD) {
            charset = Charset.forName("GBK");
            text = new String(bytes, charset);
            log.info("[TXT解析] 文件={} 非 UTF-8，降级到 GBK 解码", fileName);
        }

        // 去除 UTF-8 BOM（U+FEFF），避免污染正文
        if (StringUtils.isNotEmpty(text)
                && Objects.equals(text.charAt(0), '\uFEFF')) {
            text = text.substring(1);
        }

        // 统一换行符
        text = text.replaceAll("\\r\\n", "\n")
                .replaceAll("\\r", "\n");

        if (StringUtils.isBlank(text)) {
            return ParseResult.failure("文本文件内容为空");
        }

        // 二进制文件保护：非可打印控制字符占比过高，判定为非文本
        if (isLikelyBinary(text)) {
            return ParseResult.failure("文件包含大量非文本字符，疑似二进制文件");
        }

        log.info("[TXT解析] 文件={}，编码={}，字符数={}", fileName, charset.name(), text.length());


        ParseResult.PageContent pageContent = new ParseResult.PageContent()
                .setPageNum(1)
                .setText(text.strip());
        return new ParseResult()
                .setSuccess(true)
                .setPageContentList(List.of(pageContent))
                .setTotalPageNum(1);
    }

    /**
     * 简单启发式：非换行/制表符的控制字符占比超过阈值，视为二进制文件
     */
    private boolean isLikelyBinary(String text) {
        long nonPrintable = text.chars()
                .filter(temp -> Character.isISOControl(temp) && temp != '\n' && temp != '\t' && temp != '\r')
                .count();
        return nonPrintable > text.length() * BINARY_THRESHOLD;
    }
}