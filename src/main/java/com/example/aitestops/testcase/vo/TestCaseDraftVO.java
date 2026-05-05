package com.example.aitestops.testcase.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 测试用例草稿响应 VO。
 */
@Data
public class TestCaseDraftVO {

    private String draftCaseId;
    private String caseId;
    private String generationId;
    private String documentId;
    private String requirementExtractId;
    private String title;
    private String preconditionsJson;
    private String stepsJson;
    private String expectedResultsJson;
    private String priority;
    private String caseType;
    private String riskLevel;
    private String requirementRefsJson;
    private String riskTagsJson;
    private String reviewStatus;
    private String rawCaseJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
