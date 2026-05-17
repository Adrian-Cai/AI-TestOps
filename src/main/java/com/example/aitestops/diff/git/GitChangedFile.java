package com.example.aitestops.diff.git;

/**
 * Git 变更文件记录。
 * <p>
 * 包含变更文件的路径、类型、语言、代码行数统计和补丁内容。
 * </p>
 */
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
