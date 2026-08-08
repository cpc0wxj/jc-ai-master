package com.jichi.ragkb.service.loader;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Slf4j
public class TxtParser implements DocumentParser {

    @Override
    public String supportedType() {
        return "TXT";
    }

    @Override
    public ParseResult parse(InputStream inputStream, String fileName) {
        try {
            String text = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            text = text.replaceAll("\\r\\n", "\n").replaceAll("\\r", "\n");

            if (text.isBlank()) {
                return ParseResult.failure("文本文件内容为空");
            }

            return ParseResult.builder()
                    .success(true)
                    .pages(List.of(ParseResult.PageContent.builder()
                            .pageNum(1)
                            .text(text.strip())
                            .build()))
                    .totalPages(1)
                    .build();

        } catch (Exception e) {
            log.error("[TXT解析] 文件={}，解析失败：{}", fileName, e.getMessage(), e);
            return ParseResult.failure("TXT 解析失败：" + e.getMessage());
        }
    }
}