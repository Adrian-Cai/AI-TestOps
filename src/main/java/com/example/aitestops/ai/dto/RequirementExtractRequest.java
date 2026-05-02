package com.example.aitestops.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 需求解析请求 DTO。
 */
@Data
public class RequirementExtractRequest {

    @NotBlank(message = "documentId 不能为空")
    private String documentId;

    private String modelCode = "default";

    private String promptTemplateCode = "REQUIREMENT_EXTRACT";
}
