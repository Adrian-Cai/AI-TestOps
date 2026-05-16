package com.example.aitestops.diff.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Diff 变更文件实体。
 * <p>
 * 记录代码变更文件的路径、类型、语言、代码行数统计和初始风险评估。
 * </p>
 */
@Data
@TableName("ai_testops_diff_changed_file")
public class AiTestopsDiffChangedFile {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
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
    private Integer isTestFile;
    private Integer isCoreFile;
    private String initialRiskLevel;
    private String initialRiskReason;
    private LocalDateTime createdAt;
}
