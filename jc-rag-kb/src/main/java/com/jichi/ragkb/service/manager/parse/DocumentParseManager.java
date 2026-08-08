package com.jichi.ragkb.service.manager.parse;

import cn.hutool.core.collection.CollStreamUtil;
import com.jichi.ragkb.enums.SupportedFileType;
import com.jichi.ragkb.dto.ParseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class DocumentParseManager {
    private final Map<SupportedFileType, DocumentParseHandler> parserMap;

    public DocumentParseManager(List<DocumentParseHandler> parserList) {
        // 注入所有解析器实现，按 supportedType 建立索引
        this.parserMap = CollStreamUtil.toIdentityMap(parserList, DocumentParseHandler::getSupportedFileType);
        log.info("已加载文档解析器：{}", parserMap.keySet());
    }

    /**
     * 解析文档，根据文件类型自动选择解析器。
     *
     * @param inputStream 文件输入流
     * @param fileName    原始文件名（用于判断类型和日志）
     * @return ParseResult
     */
    public ParseResult load(InputStream inputStream, String fileName) {
        SupportedFileType fileType = SupportedFileType.fromFileName(fileName);
        DocumentParseHandler parser = parserMap.get(fileType);

        if (Objects.isNull(parser)) {
            String ext = extractExtension(fileName);
            log.warn("[文档加载] 不支持的文件类型：{}，文件：{}", ext, fileName);
            return ParseResult.failure("不支持的文件类型：" + ext + "，目前支持：PDF / DOCX / MD / TXT");
        }

        log.info("[文档加载] 开始解析：fileName={}，type={}", fileName, fileType);
        long start = System.currentTimeMillis();

        ParseResult result = parser.parse(inputStream, fileName);

        long elapsed = System.currentTimeMillis() - start;
        if (result.isSuccess()) {
            log.info("[文档加载] 解析完成：fileName={}，页数={}，耗时={}ms", fileName, result.getTotalPageNum(), elapsed);
        } else {
            log.warn("[文档加载] 解析失败：fileName={}，原因={}", fileName, result.getErrorMsg());
        }

        return result;
    }

    /**
     * 提取文件名后缀（大写），用于不支持类型的日志展示
     */
    private String extractExtension(String fileName) {
        if (fileName == null) {
            return "UNKNOWN";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0) {
            return "UNKNOWN";
        }
        return fileName.substring(dotIndex + 1).toUpperCase();
    }
}