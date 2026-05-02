package com.example.aitestops.common.util;

import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.util.Locale;

/**
 * 文件名处理工具，避免上传文件名携带路径或非法扩展名影响保存。
 */
public final class FileNameUtil {

    private FileNameUtil() {
    }

    public static String cleanOriginalFilename(String originalFilename) {
        String filename = StringUtils.cleanPath(originalFilename == null ? "" : originalFilename);
        return Path.of(filename).getFileName().toString();
    }

    public static String extension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }
}
