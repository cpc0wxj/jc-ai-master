package com.jichi.ragkb.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {
    /**
     * MinIO 服务端访问地址
     */
    private String endpoint;
    /**
     * MinIO 访问密钥（Access Key），用于身份认证
     */
    private String accessKey;
    /**
     * MinIO 私有密钥（Secret Key），用于身份认证
     */
    private String secretKey;
    /**
     * MinIO 存储桶名称
     */
    private String bucket;
}