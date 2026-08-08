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

/**
 * TXT 文件解析处理器
 * 负责解析纯文本文件内容，自动识别编码（UTF-8/GBK）并进行二进制文件保护检测
 */
@Slf4j
@Component
public class TxtParseHandler implements DocumentParseHandler {
    /**
     * UTF-8 解码后若替换字符(U+FFFD)占比超过阈值，则判定为非 UTF-8，降级到 GBK
     */
    private static final double UTF8_DECODE_FAIL_THRESHOLD = 0.01;
    /**
     * 非可打印控制字符占比阈值——超过即判定为二进制文件
     */
    private static final double BINARY_THRESHOLD = 0.05;

    /**
     * 获取支持的文件类型
     */
    @Override
    public SupportedFileType getSupportedFileType() {
        return SupportedFileType.TXT;
    }

    /**
     * 解析 TXT 文件内容
     */
    @Override
    @SneakyThrows
    public ParseResult parse(String fileName, InputStream inputStream) {
        // 读取文件全部字节
        byte[] bytes = inputStream.readAllBytes();

        // 尝试 UTF-8 解析
        Charset charset = StandardCharsets.UTF_8;
        String text = new String(bytes, charset);
        long replacementCount = text.chars().filter(temp -> Objects.equals(temp, 0xFFFD)).count();
        // 若 UTF-8 解码产生的替换字符(U+FFFD)超过阈值
        if (replacementCount > text.length() * UTF8_DECODE_FAIL_THRESHOLD) {
            // 降级到 GBK 解码
            charset = Charset.forName("GBK");
            text = new String(bytes, charset);
            log.info("TxtParseHandler.parse 降级gbk解码 fileName={}", fileName);
        }

        // 若存在 UTF-8 BOM(U+FEFF)
        if (StringUtils.isNotEmpty(text) && Objects.equals(text.charAt(0), '\uFEFF')) {
            // 去除避免污染正文
            text = text.substring(1);
        }

        // 统一换行符：将 Windows (\r\n) 和 Mac (\r) 换行统一为 Unix 风格 (\n)
        text = text.replaceAll("\\r\\n", "\n")
                .replaceAll("\\r", "\n");

        // 若文本内容为空
        if (StringUtils.isBlank(text)) {
            return ParseResult.failure("文本文件内容为空");
        }

        // 若非可打印控制字符占比过高
        if (isLikelyBinary(text)) {
            return ParseResult.failure("文件包含大量非文本字符，疑似二进制文件");
        }

        log.info("TxtParseHandler.parse fileName={},charset={},textLength={}", fileName, charset.name(), text.length());

        ParseResult.PageContent pageContent = new ParseResult.PageContent()
                .setPageNum(1)
                .setText(text.strip());
        return new ParseResult()
                .setSuccess(true)
                .setPageContentList(List.of(pageContent))
                .setTotalPageNum(1);
    }

    /**
     * 非换行/制表符的控制字符占比超过阈值，视为二进制文件
     */
    private boolean isLikelyBinary(String text) {
        // 统计非可打印控制字符数量（排除换行符、制表符、回车符）
        long nonPrintable = text.chars()
                .filter(temp -> Character.isISOControl(temp) && temp != '\n' && temp != '\t' && temp != '\r')
                .count();

        // 若控制字符占比超过阈值
        return nonPrintable > text.length() * BINARY_THRESHOLD;
    }
}