package com.example.aitestops.diff.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ai_testops_diff_risk_case_rel")
public class AiTestopsDiffRiskCaseRel {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long riskId;
    private Long caseId;
    private String documentId;
    private String requirementExtractId;
    private String caseSourceType;
    private String relationType;
    private String coverageJudgement;
    private String judgementReason;
    private BigDecimal similarityScore;
    private Integer generatedFromAi;
    private String createdBy;
    private LocalDateTime createdAt;
}
