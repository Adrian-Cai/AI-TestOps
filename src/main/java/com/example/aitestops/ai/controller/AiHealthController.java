package com.example.aitestops.ai.controller;

import com.example.aitestops.ai.client.AiClient;
import com.example.aitestops.ai.dto.AiChatRequest;
import com.example.aitestops.ai.dto.AiChatResponse;
import com.example.aitestops.ai.vo.AiHealthCheckVO;
import com.example.aitestops.common.config.AiModelProperties;
import com.example.aitestops.common.response.ApiResponse;
import com.example.aitestops.common.util.AiJsonExtractor;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI provider health check REST API.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai-testops/ai/health")
@Tag(name = "AI测试设计-模型联调")
public class AiHealthController {

    private static final String MOCK_PROVIDER = "MOCK";

    private final AiClient aiClient;
    private final AiModelProperties aiModelProperties;
    private final ObjectMapper objectMapper;

    @PostMapping("/check")
    @Operation(summary = "检查真实大模型配置和 JSON 返回能力")
    public ApiResponse<AiHealthCheckVO> check() {
        AiHealthCheckVO vo = baseResult();
        if (!vo.getConfigured()) {
            vo.setReachable(false);
            vo.setJsonValid(false);
            vo.setMessage("AI_API_BASE 或 AI_API_KEY 未配置，当前不能进行真实大模型联调");
            return ApiResponse.success(vo);
        }

        long start = System.currentTimeMillis();
        try {
            AiChatResponse response = aiClient.chat(AiChatRequest.builder()
                    .modelCode(aiModelProperties.getModelCode())
                    .modelName(aiModelProperties.getModelName())
                    .systemPrompt("你只输出 JSON，不要输出 Markdown 或解释。")
                    .userPrompt("请严格输出 {\"status\":\"ok\",\"json_valid\":true}")
                    .generationType("HEALTH_CHECK")
                    .build());
            vo.setLatencyMs(System.currentTimeMillis() - start);
            vo.setPromptTokens(response.getPromptTokens());
            vo.setCompletionTokens(response.getCompletionTokens());
            vo.setTotalTokens(response.getTotalTokens());
            objectMapper.readTree(AiJsonExtractor.extractJsonObject(response.getContent()));
            vo.setReachable(true);
            vo.setJsonValid(true);
            vo.setMessage("模型连通性检查通过");
        } catch (Exception ex) {
            vo.setLatencyMs(System.currentTimeMillis() - start);
            vo.setReachable(false);
            vo.setJsonValid(false);
            vo.setMessage(ex.getMessage());
        }
        return ApiResponse.success(vo);
    }

    private AiHealthCheckVO baseResult() {
        AiHealthCheckVO vo = new AiHealthCheckVO();
        String provider = aiModelProperties.getProvider();
        boolean mockMode = MOCK_PROVIDER.equalsIgnoreCase(provider);
        vo.setProvider(provider);
        vo.setModelCode(aiModelProperties.getModelCode());
        vo.setModelName(aiModelProperties.getModelName());
        vo.setApiBase(aiModelProperties.getApiBase());
        vo.setMockMode(mockMode);
        vo.setConfigured(mockMode || (StringUtils.hasText(aiModelProperties.getApiBase())
                && StringUtils.hasText(aiModelProperties.getApiKey())));
        return vo;
    }
}
