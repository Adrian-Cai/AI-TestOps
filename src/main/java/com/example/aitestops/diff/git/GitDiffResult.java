package com.example.aitestops.diff.git;

import java.util.List;

public record GitDiffResult(
        String repoName,
        String baseCommit,
        String headCommit,
        List<GitChangedFile> changedFiles
) {
}
