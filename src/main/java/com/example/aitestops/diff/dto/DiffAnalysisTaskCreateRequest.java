package com.example.aitestops.diff.dto;

import lombok.Data;

@Data
public class DiffAnalysisTaskCreateRequest {

    private String documentId;
    private String requirementExtractId;
    private String repoUrl;
    private String sourceBranch;
    private String targetBranch = "master";
    private DiffAnalysisOptions analysisOptions = new DiffAnalysisOptions();
    private String createdBy;
}
