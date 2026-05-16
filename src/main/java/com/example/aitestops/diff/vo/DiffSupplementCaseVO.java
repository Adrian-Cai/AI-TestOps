package com.example.aitestops.diff.vo;

import lombok.Data;

import java.util.List;

/**
 * Diff 补充用例视图对象。
 * <p>
 * 包含风险项 ID 和生成的补充用例草稿列表。
 * </p>
 */
@Data
public class DiffSupplementCaseVO {

    private Long riskId;
    private List<GeneratedCase> generatedCases;

    @Data
    public static class GeneratedCase {
        private String draftCaseId;
        private String caseId;
        private String caseTitle;
        private String priority;
        private String preconditionsJson;
        private String stepsJson;
        private String expectedResultsJson;
    }
}
