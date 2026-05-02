package com.example.aitestops.testcase.dto;

import lombok.Data;

/**
 * 测试用例生成请求 DTO，支持从 document 或 requirementExtract 生成。
 */
@Data
public class TestCaseGenerateRequest {

    private String documentId;
    private String requirementExtractId;
    private String modelCode = "default";
    private String promptTemplateCode = "TEST_CASE_GENERATE";
}
