package com.example.aitestops.diff.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Diff 风险-用例关联关系视图对象。
 * <p>
 * 包含关联的测试用例信息、覆盖判定和相似度评分。
 * </p>
 */
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
