package com.jichi.ragkb.enums;

import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/**
 * 文档解析器支持的文件类型枚举，同时维护文件后缀到类型的映射关系。
 */
@Getter
@AllArgsConstructor
public enum SupportedFileType {
    PDF("pdf"),
    DOCX("docx"),
    MD("md"),
    TXT("txt");

    private final String code;

    /**
     * 根据后缀名匹配对应的文件类型枚举
     *
     * @param extName 文件后缀名（不含点）
     * @return 匹配到的枚举值；无法识别时返回 null
     */
    public static SupportedFileType fromExtension(String extName) {
        for (SupportedFileType type : values()) {
            if (Objects.equals(extName, type.code)) {
                return type;
            }
        }

        return null;
    }
}