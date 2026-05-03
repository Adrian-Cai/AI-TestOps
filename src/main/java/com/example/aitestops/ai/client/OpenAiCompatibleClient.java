package com.example.aitestops.ai.client;

import com.example.aitestops.ai.dto.AiChatRequest;
import com.example.aitestops.ai.dto.AiChatResponse;
import com.example.aitestops.common.config.AiModelProperties;
import com.example.aitestops.common.enums.GenerationTypeEnum;
import com.example.aitestops.common.exception.BusinessException;
import com.example.aitestops.common.exception.ErrorCode;
import com.example.aitestops.common.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible HTTP Client，支持 MOCK 模式和 /v1/chat/completions 兼容接口。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleClient implements AiClient {

    private static final String MOCK_PROVIDER = "MOCK";

    private final AiModelProperties aiModelProperties;
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    @Override
    public AiChatResponse chat(AiChatRequest request) {
        String provider = aiModelProperties.getProvider();
        if (MOCK_PROVIDER.equalsIgnoreCase(provider)) {
            return mockResponse(request);
        }
        validateConfig();

        String modelName = StringUtils.hasText(request.getModelName()) ? request.getModelName() : aiModelProperties.getModelName();
        Map<String, Object> body = buildRequestBody(request, modelName);
        String endpoint = aiModelProperties.getApiBase().replaceAll("/+$", "") + "/v1/chat/completions";
        log.info("AI 请求开始: provider={}, modelCode={}, modelName={}, endpoint={}",
                provider, request.getModelCode(), modelName, endpoint);
        try {
            String responseBody = restClientBuilder.build()
                    .post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + aiModelProperties.getApiKey())
                    .body(body)
                    .retrieve()
                    .body(String.class);

            log.info("AI 请求结束: modelName={}, rawResponseLength={}", modelName, responseBody == null ? 0 : responseBody.length());
            return parseResponse(responseBody, modelName);
        } catch (RestClientException ex) {
            log.error("AI 调用失败: provider={}, modelName={}", provider, modelName, ex);
            throw new BusinessException(ErrorCode.AI_CALL_FAILED, "AI 调用失败: " + ex.getMessage(), ex);
        }
    }

    Map<String, Object> buildRequestBody(AiChatRequest request, String modelName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("messages", List.of(
                Map.of("role", "system", "content", request.getSystemPrompt()),
                Map.of("role", "user", "content", request.getUserPrompt())
        ));
        body.put("temperature", aiModelProperties.getTemperature());
        body.put("max_tokens", aiModelProperties.getMaxTokens());
        if (Boolean.TRUE.equals(aiModelProperties.getJsonMode())) {
            if (org.springframework.util.StringUtils.hasText(request.getJsonSchema())) {
                try {
                    JsonNode schemaNode = objectMapper.readTree(request.getJsonSchema());
                    Map<String, Object> responseFormat = new LinkedHashMap<>();
                    responseFormat.put("type", "json_schema");
                    Map<String, Object> jsonSchemaMap = new LinkedHashMap<>();
                    jsonSchemaMap.put("name", "structured_output");
                    jsonSchemaMap.put("strict", true);
                    jsonSchemaMap.put("schema", objectMapper.convertValue(schemaNode, Map.class));
                    responseFormat.put("json_schema", jsonSchemaMap);
                    body.put("response_format", responseFormat);
                } catch (Exception e) {
                    log.warn("json_schema 解析失败，回退使用 json_object 模式: {}", e.getMessage());
                    body.put("response_format", Map.of("type", "json_object"));
                }
            } else {
                body.put("response_format", Map.of("type", "json_object"));
            }
        }
        return body;
    }

    private void validateConfig() {
        if (!StringUtils.hasText(aiModelProperties.getApiBase()) || !StringUtils.hasText(aiModelProperties.getApiKey())) {
            throw new BusinessException(ErrorCode.AI_CONFIG_INVALID, "AI_API_BASE 或 AI_API_KEY 未配置");
        }
    }

    private AiChatResponse parseResponse(String responseBody, String modelName) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choice = root.path("choices").path(0);
            String content = choice.path("message").path("content").asText();
            if (!StringUtils.hasText(content)) {
                throw new BusinessException(ErrorCode.AI_OUTPUT_INVALID, "AI 响应 choices[0].message.content 为空");
            }
            JsonNode usage = root.path("usage");
            return AiChatResponse.builder()
                    .content(content)
                    .rawResponseJson(responseBody)
                    .promptTokens(usage.path("prompt_tokens").isMissingNode() ? null : usage.path("prompt_tokens").asInt())
                    .completionTokens(usage.path("completion_tokens").isMissingNode() ? null : usage.path("completion_tokens").asInt())
                    .totalTokens(usage.path("total_tokens").isMissingNode() ? null : usage.path("total_tokens").asInt())
                    .modelName(root.path("model").asText(modelName))
                    .finishReason(choice.path("finish_reason").asText(null))
                    .build();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.AI_OUTPUT_INVALID, "AI 响应解析失败: " + ex.getMessage(), ex);
        }
    }

    private AiChatResponse mockResponse(AiChatRequest request) {
        if (GenerationTypeEnum.TEST_CASE_GENERATE.name().equals(request.getGenerationType())) {
            return mockTestCaseResponse(request);
        }
        return mockRequirementResponse(request);
    }

    private AiChatResponse mockRequirementResponse(AiChatRequest request) {
        Map<String, Object> output = Map.of(
                "requirements", List.of(Map.of(
                        "requirement_id", "REQ_001",
                        "title", "从文档提取的核心需求",
                        "content", "MOCK 模式下根据输入文档生成的需求摘要，请配置真实模型后获取实际 AI 解析结果。",
                        "priority", "P1",
                        "risk_level", "MEDIUM",
                        "source_chunks", List.of("CHUNK_001")
                )),
                "business_rules", List.of(),
                "api_list", List.of(),
                "field_constraints", List.of(),
                "exception_cases", List.of(),
                "risks", List.of("MOCK 模式仅用于本地联调")
        );
        String content = JsonUtil.toJson(objectMapper, output);
        log.info("AI MOCK 响应生成: modelCode={}, contentLength={}", request.getModelCode(), content.length());
        return AiChatResponse.builder()
                .content(content)
                .rawResponseJson(content)
                .promptTokens(estimateTokens(request.getSystemPrompt()) + estimateTokens(request.getUserPrompt()))
                .completionTokens(estimateTokens(content))
                .totalTokens(estimateTokens(request.getSystemPrompt()) + estimateTokens(request.getUserPrompt()) + estimateTokens(content))
                .modelName(aiModelProperties.getModelName())
                .finishReason("stop")
                .build();
    }

    private AiChatResponse mockTestCaseResponse(AiChatRequest request) {
        Map<String, Object> output = Map.of(
                "test_cases", List.of(Map.of(
                        "case_id", "TC_001",
                        "title", "正常提交订单",
                        "preconditions", List.of("用户已登录", "商品库存充足"),
                        "steps", List.of(
                                Map.of("step_no", 1, "action", "进入下单页面", "expected_result", "页面正常展示"),
                                Map.of("step_no", 2, "action", "填写订单信息并提交", "expected_result", "订单提交成功")
                        ),
                        "priority", "P1",
                        "case_type", "正常场景",
                        "risk_level", "HIGH",
                        "requirement_refs", List.of("REQ_001"),
                        "risk_tags", List.of("核心链路", "订单提交")
                ))
        );
        String content = JsonUtil.toJson(objectMapper, output);
        log.info("AI MOCK 测试用例响应生成: modelCode={}, contentLength={}", request.getModelCode(), content.length());
        return AiChatResponse.builder()
                .content(content)
                .rawResponseJson(content)
                .promptTokens(estimateTokens(request.getSystemPrompt()) + estimateTokens(request.getUserPrompt()))
                .completionTokens(estimateTokens(content))
                .totalTokens(estimateTokens(request.getSystemPrompt()) + estimateTokens(request.getUserPrompt()) + estimateTokens(content))
                .modelName(aiModelProperties.getModelName())
                .finishReason("stop")
                .build();
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }
}
