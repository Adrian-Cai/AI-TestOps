package com.example.aitestops.diff.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DiffRiskCaseRelVO {

    private Long id;
    private Long caseDbId;
    private String testCaseId;
    private String caseId;
    private String caseTitle;
    private String coverageJudgement;
    private String judgementReason;
    private BigDecimal similarityScore;
}
