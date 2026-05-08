package com.example.aitestops.diff.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.aitestops.diff.dto.DiffAnalysisTaskCreateRequest;
import com.example.aitestops.diff.dto.DiffRiskActionRequest;
import com.example.aitestops.diff.dto.DiffRiskVerifyRequest;
import com.example.aitestops.diff.entity.AiTestopsDiffAnalysisTask;
import com.example.aitestops.diff.vo.DiffAnalysisReportVO;
import com.example.aitestops.diff.vo.DiffAnalysisTaskVO;
import com.example.aitestops.diff.vo.DiffSupplementCaseVO;

import java.util.List;

public interface AiTestopsDiffAnalysisTaskService extends IService<AiTestopsDiffAnalysisTask> {

    DiffAnalysisTaskVO createAndAnalyze(DiffAnalysisTaskCreateRequest request);

    List<DiffAnalysisTaskVO> listTasks(String documentId, String requirementExtractId, String status,
                                       String sourceBranch, String targetBranch);

    DiffAnalysisReportVO getReport(Long taskId);

    void applyRiskAction(Long riskId, DiffRiskActionRequest request);

    void verifyRisk(Long riskId, DiffRiskVerifyRequest request);

    DiffSupplementCaseVO generateSupplementCases(Long riskId);
}
