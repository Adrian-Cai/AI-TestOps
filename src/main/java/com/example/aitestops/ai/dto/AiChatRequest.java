package com.example.aitestops.ai.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 通用 AI 对话请求，屏蔽不同供应商的 HTTP 细节。
 */
@Data
@Builder
public class AiChatRequest {

    private String modelCode;
    private String modelName;
    private String systemPrompt;
    private String userPrompt;
    private String generationType;
    private String jsonSchema;
}
