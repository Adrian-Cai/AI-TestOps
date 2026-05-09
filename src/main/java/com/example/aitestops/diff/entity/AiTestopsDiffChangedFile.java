package com.example.aitestops.diff.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

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
