package com.example.aitestops.diff.dto;

import lombok.Data;

@Data
public class DiffAnalysisOptions {

    private Boolean includeTestFiles = false;
    private Boolean enableAiAnalysis = true;
    private Boolean enableCoverageCheck = true;
    private Boolean autoGenerateSupplementCases = false;
}
