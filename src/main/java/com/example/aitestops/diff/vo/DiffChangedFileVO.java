package com.example.aitestops.diff.vo;

import lombok.Data;

/**
 * Diff 变更文件视图对象。
 * <p>
 * 包含变更文件的路径、类型、语言、代码行数统计和初始风险评估等信息。
 * </p>
 */
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
