package com.jichi.ragkb.service.loader;

import com.jichi.ragkb.dto.ParseResult;
import com.jichi.ragkb.service.manager.parse.DocumentParseManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DocumentParseManagerTest {
    @Autowired
    private DocumentParseManager documentParseManager;

    private String extractText(ParseResult parseResult) {
        return parseResult.getPageContentList().stream()
                .map(ParseResult.PageContent::getText)
                .collect(Collectors.joining("\n"));
    }

    @Test
    void parseTxtFile() throws Exception {
        ClassPathResource classPathResource = new ClassPathResource("test-docs/hr-handbook.txt");
        try (InputStream inputStream = classPathResource.getInputStream()) {
            ParseResult parseResult = documentParseManager.load("hr-handbook.txt", inputStream);
            String text = extractText(parseResult);

            System.out.println("解析文本长度：" + text.length());
            System.out.println("解析内容前100字：" + text.substring(0, Math.min(100, text.length())));

            assertThat(parseResult.getSuccess()).isTrue();
            assertThat(text).isNotBlank();
            assertThat(text).contains("公司简介");
        }
    }

    @Test
    void parsePdf() throws Exception {
        ClassPathResource classPathResource = new ClassPathResource("test-docs/policy.pdf");
        try (InputStream inputStream = classPathResource.getInputStream()) {
            ParseResult parseResult = documentParseManager.load("policy.pdf", inputStream);
            String text = extractText(parseResult);

            System.out.println("解析文本长度：" + text.length());
            System.out.println("解析内容前100字：" + text.substring(0, Math.min(100, text.length())));
        }
    }

    @Test
    void parseMd() throws Exception {
        ClassPathResource resource = new ClassPathResource("test-docs/hr-handbook.md");
        try (InputStream inputStream = resource.getInputStream()) {
            ParseResult parseResult = documentParseManager.load("hr-handbook.md", inputStream);
            String text = extractText(parseResult);

            System.out.println("解析文本长度：" + text.length());
            System.out.println("解析内容前100字：" + text.substring(0, Math.min(100, text.length())));
        }
    }
}