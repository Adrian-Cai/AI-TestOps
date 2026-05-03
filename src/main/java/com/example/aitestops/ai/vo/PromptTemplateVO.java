package com.example.aitestops.ai.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Prompt template version response.
 */
@Data
public class PromptTemplateVO {

    private Long id;
    private String templateCode;
    private String templateName;
    private String templateType;
    private String version;
    private String promptContent;
    private String jsonSchema;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
