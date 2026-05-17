package com.example.aitestops.testcase.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 测试用例草稿人工编辑请求。
 */
@Data
public class TestCaseDraftUpdateRequest {

    @Size(max = 200, message = "标题不能超过200个字符")
    private String title;

    @Size(max = 2000, message = "前置条件不能超过2000个字符")
    private String preconditionsJson;

    @Size(max = 5000, message = "步骤不能超过5000个字符")
    private String stepsJson;

    @Size(max = 5000, message = "预期结果不能超过5000个字符")
    private String expectedResultsJson;

    @Pattern(regexp = "P0|P1|P2|P3", message = "优先级只能使用 P0、P1、P2、P3")
    private String priority;

    @Pattern(regexp = "NORMAL|EXCEPTION|BOUNDARY", message = "用例类型只能使用 NORMAL、EXCEPTION、BOUNDARY")
    private String caseType;

    @Pattern(regexp = "P0|P1|P2", message = "风险等级只能使用 P0、P1、P2")
    private String riskLevel;

    @Size(max = 2000, message = "需求引用不能超过2000个字符")
    private String requirementRefsJson;

    @Size(max = 1000, message = "风险标签不能超过1000个字符")
    private String riskTagsJson;

    @Size(max = 64, message = "评审人不能超过64个字符")
    private String reviewer;
}
