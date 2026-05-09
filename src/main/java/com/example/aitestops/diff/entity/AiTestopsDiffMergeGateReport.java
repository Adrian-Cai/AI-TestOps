package com.example.aitestops.diff.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_testops_diff_merge_gate_report")
public class AiTestopsDiffMergeGateReport {

    @TableId(type = IdType.AUTO)
    private Long id;
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
    private String reportDetail;
    private String createdBy;
    private LocalDateTime createdAt;
}
