package com.example.aitestops.document.vo;

import lombok.Data;

/**
 * 文档解析摘要响应对象。
 */
@Data
public class DocumentParseSummaryVO {

    private String documentId;
    private String parseStatus;
    private Integer rawTextLength;
    private Integer chunkCount;
    private String metadataJson;
}
