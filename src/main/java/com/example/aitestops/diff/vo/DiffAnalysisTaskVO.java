package com.example.aitestops.diff.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DiffAnalysisTaskVO {

    private Long taskId;
    private String taskCode;
    private String documentId;
    private String requirementExtractId;
    private String repoUrl;
    private String repoName;
    private String sourceBranch;
    private String targetBranch;
    private String status;
    private String failReason;
    private Integer changedFileCount;
    private Integer highRiskCount;
    private Integer mediumRiskCount;
    private Integer lowRiskCount;
    private Integer notCoveredRiskCount;
    private String mergeGateStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
