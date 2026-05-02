package com.example.aitestops.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * AI 模型调用配置，默认 MOCK 便于本地无密钥运行。
 */
@Data
@ConfigurationProperties(prefix = "ai-testops.ai")
public class AiModelProperties {

    private String provider = "MOCK";
    private String modelCode = "default";
    private String modelName = "mock-requirement-extractor";
    private String apiBase = "https://api.openai.com";
    private String apiKey;
    private BigDecimal temperature = new BigDecimal("0.2");
    private Integer maxTokens = 4096;
}
