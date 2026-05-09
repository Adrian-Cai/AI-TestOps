package com.example.aitestops.diff.vo;

import lombok.Data;

import java.util.List;

@Data
public class DiffAnalysisReportVO {

    private DiffAnalysisTaskVO task;
    private DiffMergeGateReportVO report;
    private List<DiffChangedFileVO> changedFiles;
    private List<DiffRiskItemVO> riskList;
}
