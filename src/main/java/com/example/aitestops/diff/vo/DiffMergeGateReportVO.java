package com.example.aitestops.diff.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DiffMergeGateReportVO {

    private Long reportId;
    private Long taskId;
    private String reportCode;
    private String gateStatus;
    private String gateReason;
    private Integer changedFileCount;
    private Integer changedMethodCount;
    private Integer highRiskCount;
    private Integer mediumRiskCount;
    private Integer lowRiskCount;
    private Integer coveredRiskCount;
    private Integer partialCoveredRiskCount;
    private Integer notCoveredRiskCount;
    private Integer needConfirmRiskCount;
    private Integer blockedRiskCount;
    private Integer suggestedCaseCount;
    private String suggestedRegressionModules;
    private String reportSummary;
    private LocalDateTime createdAt;
}
