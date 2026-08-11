package com.jichi.ragkb.service;

import com.jichi.ragkb.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    public String upload(Long kbId, MultipartFile file) {
        String objectPath = String.format("kb/%d/%s-%s", kbId, UUID.randomUUID().toString().substring(0, 8), file.getOriginalFilename());
        try {
            ensureBucketExists();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectPath)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
            log.info("MinioStorageService.upload path={}", objectPath);
            return objectPath;
        } catch (Exception e) {
            log.error("MinioStorageService.upload failed path={},error={}", objectPath, e.getMessage(), e);
            throw new RuntimeException("文件上传失败：" + e.getMessage(), e);
        }
    }

    /**
     * 从 MinIO 下载文件内容
     */
    public byte[] download(String objectPath) {
        try {
            InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectPath)
                    .build());
            return stream.readAllBytes();
        } catch (Exception e) {
            log.error("MinioStorageService.download failed path={},error={}", objectPath, e.getMessage(), e);
            throw new RuntimeException("文件下载失败：" + e.getMessage(), e);
        }
    }

    /**
     * 删除 MinIO 中的文件（文档删除时调用）
     */
    public void delete(String objectPath) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectPath)
                    .build());
            log.info("MinioStorageService.delete path={}", objectPath);
        } catch (Exception e) {
            // 删除失败不抛出异常，只记录警告（文件可能已经不存在）
            log.warn("MinioStorageService.delete failed path={},error={}", objectPath, e.getMessage());
        }
    }

    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(minioProperties.getBucket()).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioProperties.getBucket()).build());
            log.info("MinioStorageService.ensureBucketExists bucket={}", minioProperties.getBucket());
        }
    }
}