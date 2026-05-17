package com.example.aitestops.diff.vo;

import lombok.Data;

import java.util.List;

/**
 * Diff 风险项视图对象。
 * <p>
 * 包含风险的基本信息、等级、分类、影响模块、覆盖状态和匹配的测试用例等。
 * </p>
 */
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
