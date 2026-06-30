package org.example.wechat.common.util;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Component
public class OssUtils {

    @Value("${aliyun.oss.endpoint}")
    private String endpoint;

    @Value("${aliyun.oss.access-key-id}")
    private String accessKeyId;

    @Value("${aliyun.oss.access-key-secret}")
    private String accessKeySecret;

    @Value("${aliyun.oss.bucket-name}")
    private String bucketName;

    /**
     * 上传文件到 OSS
     * 返回格式：folder/原始文件名（前端从路径末尾截取文件名用于展示）
     * OSS key 中含短 UUID 保证唯一，不会因同名文件互相覆盖
     */
    public String uploadFile(MultipartFile file, String folder) {
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try (InputStream inputStream = file.getInputStream()) {

            // 1. 取原始文件名，防止路径注入
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isBlank()) {
                originalFilename = UUID.randomUUID().toString();
            }
            originalFilename = originalFilename.replaceAll("[/\\\\]", "_").trim();

            // 2. 拆分文件名与扩展名，拼上短 UUID 保证唯一
            int dotIndex = originalFilename.lastIndexOf('.');
            String baseName = dotIndex > 0 ? originalFilename.substring(0, dotIndex) : originalFilename;
            String suffix   = dotIndex > 0 ? originalFilename.substring(dotIndex)    : "";
            String ossKey   = folder + "/" + baseName + "_" + UUID.randomUUID().toString().substring(0, 8) + suffix;

            // 3. ContentType
            String contentType = file.getContentType();
            if (contentType == null || contentType.isBlank()) {
                contentType = "application/octet-stream";
            }

            // 4. 上传，Content-Disposition 让浏览器下载时显示原始文件名
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(contentType);
            metadata.setContentDisposition(
                    "attachment; filename=\"" +
                            new String(originalFilename.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1) +
                            "\"; filename*=UTF-8''" +
                            java.net.URLEncoder.encode(originalFilename, StandardCharsets.UTF_8).replace("+", "%20")
            );
            ossClient.putObject(bucketName, ossKey, inputStream, metadata);

            // 5. 返回 OSS key（即 context 字段的值），前端从末尾截取文件名
            return ossKey;

        } catch (Exception e) {
            throw new RuntimeException("文件上传失败", e);
        } finally {
            ossClient.shutdown();
        }
    }

    public void deleteFile(String objectKey) {
        if (objectKey == null || objectKey.trim().isEmpty()) {
            return;
        }
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            ossClient.deleteObject(bucketName, objectKey);
        } finally {
            ossClient.shutdown();
        }
    }
}