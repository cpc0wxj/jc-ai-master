package com.jichi.ragkb.service.manager.parse;

import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jichi.ragkb.dto.ParseResult;
import com.jichi.ragkb.enums.SupportedFileType;
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
     * @param fileName    原始文件名（用于判断类型和日志）
     * @param inputStream 文件输入流
     * @return ParseResult
     */
    public ParseResult load(String fileName, InputStream inputStream) {
        DocumentParseHandler documentParseHandler = getHandler(fileName);

        if (Objects.isNull(documentParseHandler)) {
            String extName = StrUtil.isNotBlank(FileUtil.extName(fileName)) ? FileUtil.extName(fileName) : "UNKNOWN";
            return ParseResult.failure("不支持的文件类型：" + extName + "，目前支持：PDF / DOCX / MD / TXT");
        }

        long start = System.currentTimeMillis();
        log.info("DocumentParseManager.load 开始解析文档 fileName={},supportedFileType={}", fileName, documentParseHandler.getSupportedFileType());
        ParseResult result = documentParseHandler.parse(inputStream, fileName);
        long elapsed = System.currentTimeMillis() - start;

        if (result.isSuccess()) {
            log.info("DocumentParseManager.load 文档解析完成 fileName={},totalPageNum={},elapsed={}", fileName, result.getTotalPageNum(), elapsed);
        } else {
            log.warn("DocumentParseManager.load 文档解析失败 fileName={},errorMsg={}", fileName, result.getErrorMsg());
        }

        return result;
    }

    /**
     * 根据文件名匹配对应的文档解析器。
     *
     * @param fileName 原始文件名
     * @return 匹配到的解析器；无法识别的文件类型返回 null
     */
    private DocumentParseHandler getHandler(String fileName) {
        String extName = StrUtil.toLowerCase(FileUtil.extName(fileName));
        SupportedFileType supportedFileType = SupportedFileType.fromExtension(extName);
        return parserMap.get(supportedFileType);
    }
}