package com.example.aitestops.review.controller;

import com.example.aitestops.common.response.ApiResponse;
import com.example.aitestops.review.service.AiTestopsReviewRecordService;
import com.example.aitestops.review.vo.ReviewRecordVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 人工评审记录 REST API。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai-testops/reviews")
@Tag(name = "AI测试设计-人工评审")
public class AiTestopsReviewController {

    private final AiTestopsReviewRecordService reviewRecordService;

    @GetMapping("/{caseId}")
    @Operation(summary = "查询某条用例的人工评审记录")
    public ApiResponse<List<ReviewRecordVO>> listReviewRecords(@PathVariable String caseId) {
        return ApiResponse.success(reviewRecordService.listByCaseId(caseId));
    }
}
