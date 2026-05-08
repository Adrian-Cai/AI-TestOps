package com.example.aitestops.diff.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ai_testops_diff_risk_item")
public class AiTestopsDiffRiskItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String documentId;
    private String requirementExtractId;
    private String riskCode;
    private String riskTitle;
    private String riskLevel;
    private String riskCategory;
    private String sourceType;
    private String sourceRuleCode;
    private String affectedModule;
    private String affectedScenarios;
    private String riskReason;
    private String testSuggestion;
    private String missingTestScenarios;
    private BigDecimal aiConfidence;
    private String coverageStatus;
    private String coverageReason;
    private String processStatus;
    private String mergeGateImpact;
    private String ignoreReason;
    private String owner;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
