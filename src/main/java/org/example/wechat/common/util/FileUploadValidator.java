package org.example.wechat.common.util;

import org.example.wechat.common.exception.BusinessException;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;

public class FileUploadValidator {

    private static final long AVATAR_MAX_SIZE = 2 * 1024 * 1024;
    private static final long CHAT_FILE_MAX_SIZE = 20 * 1024 * 1024;

    private static final Set<String> AVATAR_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> CHAT_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "webp", "gif",
            "mp4", "mov", "webm",
            "pdf", "txt", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "zip"
    );

    private FileUploadValidator() {
    }

    public static void validateAvatar(MultipartFile file) {
        validateCommon(file, AVATAR_MAX_SIZE);
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw BusinessException.badRequest("只能上传图片文件");
        }
        String extension = getExtension(file);
        if (!AVATAR_EXTENSIONS.contains(extension)) {
            throw BusinessException.badRequest("头像仅支持 jpg、jpeg、png、webp 格式");
        }
    }

    public static void validateChatFile(MultipartFile file) {
        validateCommon(file, CHAT_FILE_MAX_SIZE);
        String extension = getExtension(file);
        if (!CHAT_EXTENSIONS.contains(extension)) {
            throw BusinessException.badRequest("不支持的文件格式");
        }
        String contentType = file.getContentType();
        if (contentType != null && contentType.equalsIgnoreCase("application/x-msdownload")) {
            throw BusinessException.badRequest("不允许上传可执行文件");
        }
    }

    private static void validateCommon(MultipartFile file, long maxSize) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("文件不能为空");
        }
        if (file.getSize() > maxSize) {
            throw BusinessException.badRequest("文件大小超过限制");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw BusinessException.badRequest("文件名不能为空");
        }
        if (originalFilename.contains("/") || originalFilename.contains("\\")) {
            throw BusinessException.badRequest("文件名不合法");
        }
    }

    private static String getExtension(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        int dotIndex = originalFilename == null ? -1 : originalFilename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == originalFilename.length() - 1) {
            throw BusinessException.badRequest("文件缺少扩展名");
        }
        return originalFilename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }
}
