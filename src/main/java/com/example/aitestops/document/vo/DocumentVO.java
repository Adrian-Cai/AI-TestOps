package com.example.aitestops.document.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档详情响应对象，不直接暴露数据库实体。
 */
@Data
public class DocumentVO {

    private String documentId;
    private String title;
    private String sourceType;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String fileHash;
    private String duplicateDocumentId;
    private String parseStatus;
    private String parseError;
    private String rawTextSummary;
    private Integer rawTextLength;
    private String metadataJson;
    private LocalDateTime uploadedAt;
    private LocalDateTime parsedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
