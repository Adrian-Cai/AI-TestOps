package com.example.aitestops.diff.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Diff 分析需求来源视图对象。
 * <p>
 * 包含可用于 Diff 分析的需求文档和提取信息。
 * </p>
 */
@Data
public class DiffAnalysisSourceVO {

    private String documentId;
    private String documentTitle;
    private String parseStatus;
    private String requirementExtractId;
    private String generationId;
    private LocalDateTime documentCreatedAt;
    private LocalDateTime requirementExtractCreatedAt;
}
