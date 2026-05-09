package com.example.aitestops.diff.controller;

import com.example.aitestops.common.response.ApiResponse;
import com.example.aitestops.diff.dto.DiffAnalysisTaskCreateRequest;
import com.example.aitestops.diff.dto.DiffRiskActionRequest;
import com.example.aitestops.diff.dto.DiffRiskVerifyRequest;
import com.example.aitestops.diff.service.AiTestopsDiffAnalysisTaskService;
import com.example.aitestops.diff.vo.DiffAnalysisReportVO;
import com.example.aitestops.diff.vo.DiffAnalysisSourceVO;
import com.example.aitestops.diff.vo.DiffAnalysisTaskVO;
import com.example.aitestops.diff.vo.DiffSupplementCaseVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai-testops/diff-analysis")
@Tag(name = "AI测试设计-Diff分析")
public class AiTestopsDiffAnalysisController {

    private final AiTestopsDiffAnalysisTaskService diffAnalysisTaskService;

    @PostMapping("/tasks")
    @Operation(summary = "创建并执行代码 Diff 分析任务")
    public ApiResponse<DiffAnalysisTaskVO> createTask(@RequestBody DiffAnalysisTaskCreateRequest request) {
        return ApiResponse.success(diffAnalysisTaskService.createAndAnalyze(request));
    }

    @GetMapping("/tasks")
    @Operation(summary = "查询 Diff 分析任务列表")
    public ApiResponse<List<DiffAnalysisTaskVO>> listTasks(@RequestParam(required = false) String documentId,
                                                           @RequestParam(required = false) String requirementExtractId,
                                                           @RequestParam(required = false) String status,
                                                           @RequestParam(required = false) String sourceBranch,
                                                           @RequestParam(required = false) String targetBranch) {
        return ApiResponse.success(diffAnalysisTaskService.listTasks(documentId, requirementExtractId, status, sourceBranch, targetBranch));
    }

    @GetMapping("/sources/recent")
    @Operation(summary = "查询最近可用于 Diff 分析的需求来源")
    public ApiResponse<List<DiffAnalysisSourceVO>> listRecentSources(@RequestParam(required = false) Integer limit) {
        return ApiResponse.success(diffAnalysisTaskService.listRecentSources(limit));
    }

    @GetMapping("/repositories/branches")
    @Operation(summary = "查询 Git 仓库分支列表")
    public ApiResponse<List<String>> listRepositoryBranches(@RequestParam String repoUrl) {
        return ApiResponse.success(diffAnalysisTaskService.listRepositoryBranches(repoUrl));
    }

    @GetMapping("/tasks/{taskId}/report")
    @Operation(summary = "查询 Diff 分析报告")
    public ApiResponse<DiffAnalysisReportVO> getReport(@PathVariable Long taskId) {
        return ApiResponse.success(diffAnalysisTaskService.getReport(taskId));
    }

    @PostMapping("/risks/{riskId}/actions")
    @Operation(summary = "更新风险处理状态")
    public ApiResponse<Void> applyRiskAction(@PathVariable Long riskId, @RequestBody DiffRiskActionRequest request) {
        diffAnalysisTaskService.applyRiskAction(riskId, request);
        return ApiResponse.success();
    }

    @PostMapping("/risks/{riskId}/verify")
    @Operation(summary = "标记风险验证结果")
    public ApiResponse<Void> verifyRisk(@PathVariable Long riskId, @RequestBody DiffRiskVerifyRequest request) {
        diffAnalysisTaskService.verifyRisk(riskId, request);
        return ApiResponse.success();
    }

    @PostMapping("/risks/{riskId}/generate-supplement-cases")
    @Operation(summary = "生成 Diff 风险补充测试用例草稿")
    public ApiResponse<DiffSupplementCaseVO> generateSupplementCases(@PathVariable Long riskId) {
        return ApiResponse.success(diffAnalysisTaskService.generateSupplementCases(riskId));
    }
}
