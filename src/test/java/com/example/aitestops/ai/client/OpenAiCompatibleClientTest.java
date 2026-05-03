package com.example.aitestops.ai.client;

import com.example.aitestops.ai.dto.AiChatRequest;
import com.example.aitestops.common.config.AiModelProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatibleClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildRequestBodyShouldRequestJsonObjectWhenJsonModeEnabled() {
        AiModelProperties properties = new AiModelProperties();
        properties.setJsonMode(true);
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(properties, RestClient.builder(), objectMapper);

        Map<String, Object> body = client.buildRequestBody(baseRequest(), "deepseek-chat");

        assertThat(body).containsEntry("response_format", Map.of("type", "json_object"));
    }

    @Test
    void buildRequestBodyShouldSkipResponseFormatWhenJsonModeDisabled() {
        AiModelProperties properties = new AiModelProperties();
        properties.setJsonMode(false);
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(properties, RestClient.builder(), objectMapper);

        Map<String, Object> body = client.buildRequestBody(baseRequest(), "deepseek-chat");

        assertThat(body).doesNotContainKey("response_format");
    }

    private AiChatRequest baseRequest() {
        return AiChatRequest.builder()
                .modelCode("default")
                .modelName("deepseek-chat")
                .systemPrompt("只输出 JSON")
                .userPrompt("输出 {\"status\":\"ok\"}")
                .generationType("HEALTH_CHECK")
                .build();
    }
}
