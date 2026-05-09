package com.example.aitestops.diff.git;

public record GitChangedFile(
        String oldFilePath,
        String newFilePath,
        String changeType,
        String language,
        int additions,
        int deletions,
        String patch
) {
}
