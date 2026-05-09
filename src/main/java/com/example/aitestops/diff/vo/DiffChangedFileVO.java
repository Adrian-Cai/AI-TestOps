package com.example.aitestops.diff.vo;

import lombok.Data;

@Data
public class DiffChangedFileVO {

    private Long changedFileId;
    private String oldFilePath;
    private String newFilePath;
    private String changeType;
    private String language;
    private String fileRole;
    private Integer additions;
    private Integer deletions;
    private Integer changes;
    private String patch;
    private String patchSummary;
    private Boolean testFile;
    private String initialRiskLevel;
    private String initialRiskReason;
}
