package com.example.aitestops.ai.controller;

import com.example.aitestops.ai.dto.PromptTemplateVersionCreateRequest;
import com.example.aitestops.ai.service.AiTestopsPromptTemplateService;
import com.example.aitestops.ai.vo.PromptQualityVO;
import com.example.aitestops.ai.vo.PromptTemplateVO;
import com.example.aitestops.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Prompt template versioning and quality loop REST API.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai-testops/prompts")
@Tag(name = "AI测试设计-Prompt版本")
public class AiPromptTemplateController {

    private final AiTestopsPromptTemplateService promptTemplateService;

    @GetMapping
    @Operation(summary = "查询 Prompt 模板版本列表")
    public ApiResponse<List<PromptTemplateVO>> listVersions(@RequestParam(required = false) String templateCode,
                                                            @RequestParam(required = false) String templateType) {
        return ApiResponse.success(promptTemplateService.listVersions(templateCode, templateType));
    }

    @PostMapping
    @Operation(summary = "创建 Prompt 模板新版本")
    public ApiResponse<PromptTemplateVO> createVersion(@Valid @RequestBody PromptTemplateVersionCreateRequest request) {
        return ApiResponse.success(promptTemplateService.createVersion(request));
    }

    @PostMapping("/{templateCode}/versions/{version}/activate")
    @Operation(summary = "启用指定 Prompt 模板版本")
    public ApiResponse<PromptTemplateVO> activateVersion(@PathVariable String templateCode, @PathVariable String version) {
        return ApiResponse.success(promptTemplateService.activateVersion(templateCode, version));
    }

    @GetMapping("/quality")
    @Operation(summary = "按 Prompt 版本汇总生成质量指标")
    public ApiResponse<List<PromptQualityVO>> listQuality(@RequestParam(required = false) String templateCode,
                                                          @RequestParam(required = false) String generationType) {
        return ApiResponse.success(promptTemplateService.listQuality(templateCode, generationType));
    }
}
