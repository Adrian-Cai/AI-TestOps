package com.example.aitestops.export.vo;

import java.util.List;

/**
 * 测试用例导出专用视图，避免直接暴露完整正式用例 VO。
 */
public record ExportTestCaseItem(
        String title,
        String priority,
        List<String> preconditions,
        List<String> testSteps,
        List<String> expectedResults,
        List<String> riskTags
) {
}
