package com.example.aitestops.ai.controller;

import com.example.aitestops.ai.service.AiTestopsGenerationRecordService;
import com.example.aitestops.ai.vo.GenerationRecordVO;
import com.example.aitestops.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 生成记录 REST API。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai-testops/generations")
@Tag(name = "AI测试设计-生成记录")
public class AiGenerationController {

    private final AiTestopsGenerationRecordService generationRecordService;

    @GetMapping("/{generationId}")
    @Operation(summary = "查询 AI 生成记录")
    public ApiResponse<GenerationRecordVO> getGenerationRecord(@PathVariable String generationId) {
        return ApiResponse.success(generationRecordService.getGenerationRecord(generationId));
    }
}
