package com.example.aitestops.diff.service;

/**
 * Diff 分析报告刷新服务。
 * <p>
 * 负责根据最新的风险项和变更文件重新计算合并准入状态，
 * 并更新或创建对应的合并准入报告。
 * </p>
 */
public interface DiffReportRefreshService {

    /**
     * 刷新指定任务的合并准入报告。
     * <p>
     * 重新加载风险项和变更文件，计算准入状态，更新报告统计信息，
     * 并同步更新分析任务的汇总数据。
     * </p>
     *
     * @param taskId Diff 分析任务 ID
     */
    void refresh(Long taskId);
}
