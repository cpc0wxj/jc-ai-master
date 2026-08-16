package com.jichi.ragkb.service;

import com.jichi.ragkb.config.MinioProperties;
import io.minio.*;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

/**
 * MinIO 文件存储服务
 * 提供文件上传、下载和删除功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageService {
    private final MinioClient minioClient;

    private final MinioProperties minioProperties;

    /**
     * 上传文件到 MinIO，返回对象路径
     * 路径格式：kb/{kbId}/{uuid}-{originalFileName}
     */
    @SneakyThrows
    public String upload(Long kbId, MultipartFile file) {
        String objectPath = String.format("kb/%d/%s-%s", kbId, UUID.randomUUID().toString().substring(0, 8), file.getOriginalFilename());
        ensureBucketExists();
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(minioProperties.getBucket())
                .object(objectPath)
                .stream(file.getInputStream(), file.getSize(), -1)
                .contentType(file.getContentType())
                .build());
        log.info("MinioStorageService.upload path={}", objectPath);
        return objectPath;
    }

    /**
     * 从 MinIO 下载文件内容
     */
    @SneakyThrows
    public byte[] download(String objectPath) {
        InputStream inputStream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(minioProperties.getBucket())
                .object(objectPath)
                .build());
        return inputStream.readAllBytes();
    }

    /**
     * 删除 MinIO 中的文件（文档删除时调用）
     */
    @SneakyThrows
    public void delete(String objectPath) {
        if (StringUtils.isBlank(objectPath)) {
            return;
        }

        RemoveObjectArgs removeObjectArgs = RemoveObjectArgs.builder()
                .bucket(minioProperties.getBucket())
                .object(objectPath)
                .build();
        minioClient.removeObject(removeObjectArgs);
        log.info("MinioStorageService.delete path={}", objectPath);
    }

    private void ensureBucketExists() throws Exception {
        BucketExistsArgs bucketExistsArgs = BucketExistsArgs.builder()
                .bucket(minioProperties.getBucket()).build();
        boolean exists = minioClient.bucketExists(bucketExistsArgs);
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioProperties.getBucket()).build());
            log.info("MinioStorageService.ensureBucketExists bucket={}", minioProperties.getBucket());
        }
    }
}