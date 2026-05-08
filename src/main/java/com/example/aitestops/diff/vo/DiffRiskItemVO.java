package com.example.aitestops.diff.vo;

import lombok.Data;

import java.util.List;

@Data
public class DiffRiskItemVO {

    private Long riskId;
    private String riskCode;
    private String riskTitle;
    private String riskLevel;
    private String riskCategory;
    private String sourceType;
    private String affectedModule;
    private String affectedScenarios;
    private String riskReason;
    private String testSuggestion;
    private String missingTestScenarios;
    private String coverageStatus;
    private String coverageReason;
    private String processStatus;
    private String mergeGateImpact;
    private List<DiffRiskCaseRelVO> matchedCases;
}
