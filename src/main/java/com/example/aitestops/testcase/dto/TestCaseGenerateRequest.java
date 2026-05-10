package com.example.aitestops.testcase.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 测试用例生成请求 DTO，支持从 document 或 requirementExtract 生成。
 */
@Data
public class TestCaseGenerateRequest {

    @Size(max = 128, message = "documentId 不能超过128个字符")
    private String documentId;

    @Size(max = 128, message = "requirementExtractId 不能超过128个字符")
    private String requirementExtractId;

    @Size(max = 64, message = "模型编码不能超过64个字符")
    private String modelCode = "default";

    @Size(max = 64, message = "模板编码不能超过64个字符")
    private String promptTemplateCode = "TEST_CASE_GENERATE";

    @Min(value = 1, message = "用例数量最少为1")
    @Max(value = 50, message = "用例数量最多为50")
    private Integer caseCount;
}
