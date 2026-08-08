package com.jichi.ragkb.manager.loader;

import com.jichi.ragkb.service.loader.ParseResult;

import java.io.InputStream;

public interface DocumentParser {
    /**
     * 支持的文件类型（大写），如 "PDF"、"DOCX"
     */
    String supportedType();

    /**
     * 解析文件，返回解析结果
     */
    ParseResult parse(InputStream inputStream, String fileName);
}