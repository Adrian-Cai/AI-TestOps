package com.example.aitestops.diff.enums;

/**
 * Diff 分析任务状态枚举。
 * <p>
 * 定义任务的生命周期状态：待处理、运行中、成功、失败、已取消。
 * </p>
 */
public enum DiffAnalysisTaskStatusEnum {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELED
}
