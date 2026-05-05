package com.example.aitestops.testcase.dto;

import lombok.Data;

/**
 * 测试用例草稿人工编辑请求。
 */
@Data
public class TestCaseDraftUpdateRequest {

    private String title;
    private String preconditionsJson;
    private String stepsJson;
    private String expectedResultsJson;
    private String priority;
    private String caseType;
    private String riskLevel;
    private String requirementRefsJson;
    private String riskTagsJson;
    private String reviewer;
}
