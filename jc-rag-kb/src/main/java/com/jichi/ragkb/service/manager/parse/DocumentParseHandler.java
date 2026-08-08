package com.jichi.ragkb.service.manager.parse;

import com.jichi.ragkb.dto.ParseResult;
import com.jichi.ragkb.enums.SupportedFileType;

import java.io.InputStream;

public interface DocumentParseHandler {
    /**
     * 支持的文件类型
     */
    SupportedFileType getSupportedFileType();

    /**
     * 解析文件，返回解析结果
     */
    ParseResult parse(String fileName, InputStream inputStream);
}