package com.example.aitestops.ai.controller;

import com.example.aitestops.ai.dto.RequirementExtractRequest;
import com.example.aitestops.ai.service.AiTestopsRequirementExtractService;
import com.example.aitestops.ai.vo.RequirementExtractVO;
import com.example.aitestops.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 需求解析 REST API。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai-testops/ai/requirements")
@Tag(name = "AI测试设计-AI需求解析")
public class AiRequirementController {

    private final AiTestopsRequirementExtractService requirementExtractService;

    @PostMapping("/extract")
    @Operation(summary = "基于 documentId 调用 AI 提取结构化需求")
    public ApiResponse<RequirementExtractVO> extractRequirements(@Valid @RequestBody RequirementExtractRequest request) {
        return ApiResponse.success(requirementExtractService.extractRequirements(request));
    }
}
