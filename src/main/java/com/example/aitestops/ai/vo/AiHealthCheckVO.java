package com.example.aitestops.ai.vo;

import lombok.Data;

/**
 * AI provider connectivity check result without exposing secrets.
 */
@Data
public class AiHealthCheckVO {

    private String provider;
    private String modelCode;
    private String modelName;
    private String apiBase;
    private Boolean mockMode;
    private Boolean configured;
    private Boolean reachable;
    private Boolean jsonValid;
    private Long latencyMs;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private String message;
}
