package com.example.aitestops.ai.vo;

import lombok.Data;

/**
 * Aggregated quality metrics for one Prompt template version.
 */
@Data
public class PromptQualityVO {

    private String templateCode;
    private String version;
    private String templateName;
    private String templateType;
    private Integer enabled;
    private String generationType;
    private Long totalGenerations;
    private Long successGenerations;
    private Long failedGenerations;
    private Long validationFailedGenerations;
    private Long draftCount;
    private Long approvedCount;
    private Long rejectedCount;
    private Long editedCount;
    private Double successRate;
    private Double validationPassRate;
    private Double approveRate;
    private Double avgTokenInput;
    private Double avgTokenOutput;
}
