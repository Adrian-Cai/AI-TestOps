package com.example.aitestops.diff.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Diff 分析任务实体。
 * <p>
 * 记录代码 Diff 分析任务的基本信息、分支配置、执行状态和风险统计。
 * </p>
 */
@Data
@TableName("ai_testops_diff_analysis_task")
public class AiTestopsDiffAnalysisTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskCode;
    private String documentId;
    private String requirementExtractId;
    private String repoUrl;
    private String repoName;
    private String sourceBranch;
    private String targetBranch;
    private String baseCommit;
    private String headCommit;
    private String analysisOptions;
    private String status;
    private String failReason;
    private Integer changedFileCount;
    private Integer changedMethodCount;
    private Integer highRiskCount;
    private Integer mediumRiskCount;
    private Integer lowRiskCount;
    private Integer notCoveredRiskCount;
    private String mergeGateStatus;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
