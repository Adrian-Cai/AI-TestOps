package com.example.aitestops.diff.enums;

/**
 * Diff 合并准入门禁状态枚举。
 * <p>
 * 定义合并准入的结论状态：通过、警告、阻塞、需人工评审。
 * </p>
 */
public enum DiffMergeGateStatusEnum {
    PASS,
    WARNING,
    BLOCK,
    MANUAL_REVIEW
}
