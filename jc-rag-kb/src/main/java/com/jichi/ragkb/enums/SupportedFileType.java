package com.jichi.ragkb.enums;

import cn.hutool.core.io.FileUtil;
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
     * 根据文件名解析出对应的文件类型枚举
     *
     * @param fileName 文件名
     * @return 匹配到的枚举值；无法识别时返回 null
     */
    public static SupportedFileType fromFileName(String fileName) {
        String extName = FileUtil.extName(fileName);
        extName = StrUtil.toLowerCase(extName);

        for (SupportedFileType type : values()) {
            if (Objects.equals(extName, type.code)) {
                return type;
            }
        }

        return null;
    }
}