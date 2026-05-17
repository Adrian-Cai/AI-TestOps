package com.example.aitestops.diff.dto;

import lombok.Data;

/**
 * Diff 分析选项配置 DTO。
 * <p>
 * 控制分析过程中的行为选项，包括是否包含测试文件、
 * 是否启用 AI 分析、是否启用覆盖检查、是否自动生成补充用例等。
 * </p>
 */
@Data
public class DiffAnalysisOptions {

    private Boolean includeTestFiles = false;
    private Boolean enableAiAnalysis = true;
    private Boolean enableCoverageCheck = true;
    private Boolean autoGenerateSupplementCases = false;
}
