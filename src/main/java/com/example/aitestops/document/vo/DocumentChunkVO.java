package com.example.aitestops.document.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档分块响应对象。
 */
@Data
public class DocumentChunkVO {

    private String chunkId;
    private String documentId;
    private Integer chunkIndex;
    private String sectionTitle;
    private String chunkText;
    private Integer tokenCount;
    private String extraJson;
    private LocalDateTime createdAt;
}
