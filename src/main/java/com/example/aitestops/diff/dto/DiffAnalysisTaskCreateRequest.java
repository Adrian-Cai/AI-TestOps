package com.example.aitestops.diff.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Diff 分析任务创建请求 DTO。
 * <p>
 * 包含仓库地址、源分支、目标分支等必要信息，
 * 以及可选的需求文档 ID 和分析选项配置。
 * </p>
 */
@Data
public class DiffAnalysisTaskCreateRequest {

    @Size(max = 128, message = "documentId 不能超过128个字符")
    private String documentId;

    @Size(max = 128, message = "requirementExtractId 不能超过128个字符")
    private String requirementExtractId;

    @NotBlank(message = "仓库地址不能为空")
    @Size(max = 500, message = "仓库地址不能超过500个字符")
    private String repoUrl;

    @NotBlank(message = "变更分支不能为空")
    @Size(max = 255, message = "分支名不能超过255个字符")
    private String sourceBranch;

    @Size(max = 255, message = "分支名不能超过255个字符")
    private String targetBranch = "master";

    @Valid
    private DiffAnalysisOptions analysisOptions = new DiffAnalysisOptions();

    @Size(max = 64, message = "创建人不能超过64个字符")
    private String createdBy;
}
