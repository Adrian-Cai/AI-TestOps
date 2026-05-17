package com.example.aitestops.diff.vo;

import lombok.Data;

import java.util.List;

/**
 * Diff 分析报告视图对象。
 * <p>
 * 包含分析任务、合并准入报告、变更文件列表和风险项列表。
 * </p>
 */
@Data
public class DiffAnalysisReportVO {

    private DiffAnalysisTaskVO task;
    private DiffMergeGateReportVO report;
    private List<DiffChangedFileVO> changedFiles;
    private List<DiffRiskItemVO> riskList;
}
