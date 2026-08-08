package com.jichi.ragkb.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

@Getter
@Setter
@Accessors(chain = true)
public class ParseResult {
    /**
     * 解析是否成功
     */
    private boolean success;
    /**
     * 错误信息（success=false 时有值）
     */
    private String errorMsg;
    /**
     * 解析出的页面列表（PDF 按页，其他格式整体算一页）
     */
    private List<PageContent> pageContentList;
    /**
     * 文档总页数
     */
    private int totalPageNum;
    /**
     * 文档标题（如果能识别）
     */
    private String title;

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class PageContent {
        /**
         * 页码（1-based）
         */
        private int pageNum;
        /**
         * 该页的纯文本内容
         */
        private String text;
        /**
         * 该页识别到的章节标题（可能为空）
         */
        private String sectionTitle;
    }

    public static ParseResult failure(String errorMsg) {
        return new ParseResult()
                .setSuccess(false)
                .setErrorMsg(errorMsg)
                .setPageContentList(List.of());
    }
}