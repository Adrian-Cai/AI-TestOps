package com.example.aitestops.common.config;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * 文件上传配置，控制本地保存目录、大小限制和允许扩展名。
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ai-testops.file")
public class FileUploadProperties {

    @NotNull
    private String uploadDir = "./data/uploads";

    @NotNull
    private DataSize maxSize = DataSize.ofMegabytes(50);

    @NotEmpty
    private List<String> allowedExtensions = List.of("md", "txt", "ppt", "pptx", "xls", "xlsx", "pdf", "doc", "docx");
}
