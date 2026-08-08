package com.jichi.ragkb.service.manager.parse;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Maps;
import com.jichi.ragkb.dto.ParseResult;
import com.jichi.ragkb.enums.SupportedFileType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;

/**
 * 文档解析管理器
 */
@Slf4j
@Component
public class DocumentParseManager implements BeanPostProcessor {
    private static final Map<SupportedFileType, DocumentParseHandler> parserMap = Maps.newHashMap();

    @Override
    public Object postProcessBeforeInitialization(@NonNull Object bean, @NonNull String beanName) throws BeansException {
        //实现DocumentParseHandler接口的类
        if (bean instanceof DocumentParseHandler handler) {
            //将其放入parserMap
            if (parserMap.containsKey(handler.getSupportedFileType())) {
                throw new IllegalStateException("DocumentParseManager.postProcessBeforeInitialization 重复注册DocumentParseHandler");
            }
            parserMap.put(handler.getSupportedFileType(), handler);
            log.info("DocumentParseManager.postProcessBeforeInitialization 已加载文档解析器={}", handler.getSupportedFileType());
        }
        return bean;
    }

    /**
     * 解析文档
     *
     * @param fileName    原始文件名
     * @param inputStream 文件输入流
     */
    public ParseResult load(String fileName, InputStream inputStream) {
        DocumentParseHandler documentParseHandler = getHandler(fileName);

        if (Objects.isNull(documentParseHandler)) {
            String extName = StrUtil.isNotBlank(FileUtil.extName(fileName)) ? FileUtil.extName(fileName) : "UNKNOWN";
            return ParseResult.failure("不支持的文件类型：" + extName + "，目前支持：PDF / DOCX / MD / TXT");
        }

        long start = System.currentTimeMillis();
        log.info("DocumentParseManager.load 开始解析文档 fileName={},supportedFileType={}", fileName, documentParseHandler.getSupportedFileType());
        ParseResult result = documentParseHandler.parse(fileName, inputStream);
        long elapsed = System.currentTimeMillis() - start;

        if (result.isSuccess()) {
            log.info("DocumentParseManager.load 文档解析完成 fileName={},totalPageNum={},elapsed={}", fileName, result.getTotalPageNum(), elapsed);
        } else {
            log.warn("DocumentParseManager.load 文档解析失败 fileName={},errorMsg={}", fileName, result.getErrorMsg());
        }

        return result;
    }

    /**
     * 根据文件名匹配对应的文档解析器
     */
    private DocumentParseHandler getHandler(String fileName) {
        String extName = StrUtil.toLowerCase(FileUtil.extName(fileName));
        SupportedFileType supportedFileType = SupportedFileType.fromExtension(extName);
        return parserMap.get(supportedFileType);
    }
}