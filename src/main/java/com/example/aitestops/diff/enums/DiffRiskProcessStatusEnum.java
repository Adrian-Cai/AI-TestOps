package com.example.aitestops.diff.enums;

/**
 * Diff 风险处理状态枚举。
 * <p>
 * 定义风险项的处理流程状态：待处理、已确认、已忽略、等待用例、
 * 等待测试、测试中、已通过、已失败、已阻塞、已关闭。
 * </p>
 */
public enum DiffRiskProcessStatusEnum {
    PENDING,
    CONFIRMED,
    IGNORED,
    WAIT_CASE,
    WAIT_TEST,
    TESTING,
    PASSED,
    FAILED,
    BLOCKED,
    CLOSED
}
