package com.example.aitestops.ai.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 通用 AI 对话响应，保留原始响应和 token 统计。
 */
@Data
@Builder
public class AiChatResponse {

    private String content;
    private String rawResponseJson;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private String modelName;
}
