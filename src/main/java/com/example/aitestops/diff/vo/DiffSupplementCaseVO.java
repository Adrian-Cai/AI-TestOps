package com.example.aitestops.diff.vo;

import lombok.Data;

import java.util.List;

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
