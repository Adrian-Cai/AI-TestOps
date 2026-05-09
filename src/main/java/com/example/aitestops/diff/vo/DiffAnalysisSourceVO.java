package com.example.aitestops.diff.vo;

import lombok.Data;

import java.time.LocalDateTime;

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
