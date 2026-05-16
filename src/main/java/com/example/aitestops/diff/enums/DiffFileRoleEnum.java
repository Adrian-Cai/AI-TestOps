package com.example.aitestops.diff.enums;

/**
 * Diff 文件角色枚举。
 * <p>
 * 定义变更文件在系统中的角色类型：控制器、服务层、数据访问层、
 * SQL、配置、定时任务、消息队列、认证、测试、其他。
 * </p>
 */
public enum DiffFileRoleEnum {
    CONTROLLER,
    SERVICE,
    DAO,
    SQL,
    CONFIG,
    JOB,
    MQ,
    AUTH,
    TEST,
    OTHER
}
