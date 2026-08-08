package com.jichi.ragkb.service.loader;

import com.jichi.ragkb.manager.loader.DocumentLoaderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DocumentLoaderServiceTest {

    @Autowired
    private DocumentLoaderService loaderService;

    private String extractText(ParseResult result) {
        return result.getPageContentList().stream()
                .map(ParseResult.PageContent::getText)
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    @Test
    void parseTxtFile() throws Exception {
        ClassPathResource resource = new ClassPathResource("test-docs/hr-handbook.txt");
        try (InputStream is = resource.getInputStream()) {
            ParseResult result = loaderService.load(is, "hr-handbook.txt");
            String text = extractText(result);

            System.out.println("解析文本长度：" + text.length());
            System.out.println("解析内容前100字：" + text.substring(0, Math.min(100, text.length())));

            assertThat(result.isSuccess()).isTrue();
            assertThat(text).isNotBlank();
            assertThat(text).contains("Spring Boot");
        }
    }

    @Test
    void parsePdf() throws Exception {
        ClassPathResource resource = new ClassPathResource("test-docs/policy.pdf");
        try (InputStream is = resource.getInputStream()) {
            ParseResult result = loaderService.load(is, "policy.pdf");
            String text = extractText(result);

            System.out.println("解析文本长度：" + text.length());
            System.out.println("解析内容前100字：" + text.substring(0, Math.min(100, text.length())));
        }
    }

    @Test
    void parseMd() throws Exception {
        ClassPathResource resource = new ClassPathResource("test-docs/hr-handbook.md");
        try (InputStream is = resource.getInputStream()) {
            ParseResult result = loaderService.load(is, "hr-handbook.md");
            String text = extractText(result);

            System.out.println("解析文本长度：" + text.length());
            System.out.println("解析内容前100字：" + text.substring(0, Math.min(100, text.length())));
        }
    }

    @Test
    void unsupportedTypeThrowsException() {
        InputStream emptyStream = InputStream.nullInputStream();
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> loaderService.load(emptyStream, "test.xyz")
        );
    }
}