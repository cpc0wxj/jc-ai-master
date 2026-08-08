package com.jichi.ragkb.service.manager.parse;

import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
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
        String extName = FileUtil.extName(fileName);
        extName = StrUtil.toLowerCase(extName);
        SupportedFileType supportedFileType = SupportedFileType.fromExtension(extName);
        DocumentParseHandler documentParseHandler = parserMap.get(supportedFileType);

        if (Objects.isNull(documentParseHandler)) {
            String displayExt = StrUtil.isBlank(extName) ? "UNKNOWN" : extName;
            return ParseResult.failure("不支持的文件类型：" + displayExt + "，目前支持：PDF / DOCX / MD / TXT");
        }

        long start = System.currentTimeMillis();
        log.info("[文档加载] 开始解析：fileName={}，type={}", fileName, supportedFileType);
        ParseResult result = documentParseHandler.parse(inputStream, fileName);
        long elapsed = System.currentTimeMillis() - start;

        if (result.isSuccess()) {
            log.info("[文档加载] 解析完成：fileName={}，页数={}，耗时={}ms", fileName, result.getTotalPageNum(), elapsed);
        } else {
            log.warn("[文档加载] 解析失败：fileName={}，原因={}", fileName, result.getErrorMsg());
        }

        return result;
    }
}