package com.example.aitestops.testcase.controller;

import com.example.aitestops.common.response.ApiResponse;
import com.example.aitestops.testcase.dto.TestCaseDraftBatchApproveRequest;
import com.example.aitestops.testcase.dto.TestCaseDraftReviewRequest;
import com.example.aitestops.testcase.dto.TestCaseDraftUpdateRequest;
import com.example.aitestops.testcase.dto.TestCaseGenerateRequest;
import com.example.aitestops.testcase.service.AiTestopsTestCaseDraftService;
import com.example.aitestops.testcase.service.AiTestopsTestCaseService;
import com.example.aitestops.testcase.vo.TestCaseDraftVO;
import com.example.aitestops.testcase.vo.TestCaseGenerateVO;
import com.example.aitestops.testcase.vo.TestCaseVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 测试用例模块 REST API。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai-testops/testcases")
@Tag(name = "AI测试设计-测试用例")
public class AiTestopsTestCaseController {

    private final AiTestopsTestCaseDraftService draftService;
    private final AiTestopsTestCaseService testCaseService;

    @PostMapping("/generate")
    @Operation(summary = "基于 documentId 或 requirementExtractId 生成测试用例草稿")
    public ApiResponse<TestCaseGenerateVO> generateDrafts(@Valid @RequestBody TestCaseGenerateRequest request) {
        return ApiResponse.success(draftService.generateDrafts(request));
    }

    @GetMapping("/drafts")
    @Operation(summary = "查询测试用例草稿列表")
    public ApiResponse<List<TestCaseDraftVO>> listDrafts(@RequestParam(required = false) String documentId,
                                                         @RequestParam(required = false) String generationId) {
        return ApiResponse.success(draftService.listDrafts(documentId, generationId));
    }

    @GetMapping("/drafts/{draftCaseId}")
    @Operation(summary = "查询测试用例草稿详情")
    public ApiResponse<TestCaseDraftVO> getDraft(@PathVariable String draftCaseId) {
        return ApiResponse.success(draftService.getDraft(draftCaseId));
    }

    @PutMapping("/drafts/{draftCaseId}")
    @Operation(summary = "人工编辑测试用例草稿")
    public ApiResponse<TestCaseDraftVO> updateDraft(@PathVariable String draftCaseId,
                                                    @Valid @RequestBody TestCaseDraftUpdateRequest request) {
        return ApiResponse.success(draftService.updateDraft(draftCaseId, request));
    }

    @PostMapping("/drafts/{draftCaseId}/approve")
    @Operation(summary = "确认单条草稿并写入正式测试用例")
    public ApiResponse<TestCaseVO> approveDraft(@PathVariable String draftCaseId,
                                                @Valid @RequestBody(required = false) TestCaseDraftReviewRequest request) {
        return ApiResponse.success(draftService.approveDraft(draftCaseId, request));
    }

    @PostMapping("/drafts/{draftCaseId}/reject")
    @Operation(summary = "驳回单条草稿并记录评审原因")
    public ApiResponse<TestCaseDraftVO> rejectDraft(@PathVariable String draftCaseId,
                                                    @Valid @RequestBody(required = false) TestCaseDraftReviewRequest request) {
        return ApiResponse.success(draftService.rejectDraft(draftCaseId, request));
    }

    @PostMapping("/drafts/batch-approve")
    @Operation(summary = "批量确认测试用例草稿")
    public ApiResponse<List<TestCaseVO>> batchApprove(@Valid @RequestBody TestCaseDraftBatchApproveRequest request) {
        return ApiResponse.success(draftService.batchApprove(request));
    }

    @GetMapping
    @Operation(summary = "查询正式测试用例列表")
    public ApiResponse<List<TestCaseVO>> listCases(@RequestParam(required = false) String documentId,
                                                   @RequestParam(required = false) String requirementExtractId) {
        return ApiResponse.success(testCaseService.listCases(documentId, requirementExtractId));
    }
}
