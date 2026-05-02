package com.example.aitestops.testcase.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 正式测试用例响应 VO。
 */
@Data
public class TestCaseVO {

    private String testCaseId;
    private String sourceDraftCaseId;
    private String caseId;
    private String generationId;
    private String documentId;
    private String requirementExtractId;
    private String title;
    private String preconditionsJson;
    private String stepsJson;
    private String priority;
    private String caseType;
    private String riskLevel;
    private String requirementRefsJson;
    private String riskTagsJson;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
