package com.jichi.ragkb.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MinIO 配置属性，对应 application.yml 中 minio.* 配置块。
 * 通过 @ConfigurationProperties 整体绑定，替代逐个 @Value 注入。
 */
@Component
@ConfigurationProperties(prefix = "minio")
@Getter
@Setter
public class MinioProperties {
    private String endpoint;
    private String accessKey;
    private String secretKey;
}
