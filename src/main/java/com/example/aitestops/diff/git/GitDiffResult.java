package com.example.aitestops.diff.git;

import java.util.List;

/**
 * Git Diff 结果记录。
 * <p>
 * 包含仓库名称、基准提交、目标提交和变更文件列表。
 * </p>
 */
public record GitDiffResult(
        String repoName,
        String baseCommit,
        String headCommit,
        List<GitChangedFile> changedFiles
) {
}
