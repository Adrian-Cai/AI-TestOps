package com.example.aitestops.ai.controller;

import com.example.aitestops.ai.service.AiTestopsValidationResultService;
import com.example.aitestops.ai.vo.ValidationResultVO;
import com.example.aitestops.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 输出校验结果 REST API。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai-testops/validations")
@Tag(name = "AI测试设计-校验结果")
public class AiValidationController {

    private final AiTestopsValidationResultService validationResultService;

    @GetMapping("/{generationId}")
    @Operation(summary = "查询某次生成的校验结果")
    public ApiResponse<List<ValidationResultVO>> listValidationResults(@PathVariable String generationId) {
        return ApiResponse.success(validationResultService.listByGenerationId(generationId));
    }
}
