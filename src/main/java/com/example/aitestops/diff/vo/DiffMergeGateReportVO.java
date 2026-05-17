package com.example.aitestops.diff.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Diff 合并准入报告视图对象。
 * <p>
 * 包含报告基本信息、准入状态、风险统计、覆盖统计和回归建议等。
 * </p>
 */
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
