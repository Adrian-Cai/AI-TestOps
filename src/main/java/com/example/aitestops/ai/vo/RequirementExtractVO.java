package com.example.aitestops.ai.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 需求解析结果响应 VO。
 */
@Data
public class RequirementExtractVO {

    private String requirementExtractId;
    private String generationId;
    private String documentId;
    private String requirementsJson;
    private String businessRulesJson;
    private String apiListJson;
    private String fieldConstraintsJson;
    private String exceptionCasesJson;
    private String risksJson;
    private String rawOutputJson;
    private LocalDateTime createdAt;
}
