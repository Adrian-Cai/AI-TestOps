package com.example.aitestops.ai.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 大模型生成记录响应 VO。
 */
@Data
public class GenerationRecordVO {

    private String generationId;
    private String documentId;
    private String requirementExtractId;
    private String promptTemplateCode;
    private String modelCode;
    private String modelName;
    private String generationType;
    private String inputSnapshotJson;
    private String outputJson;
    private String status;
    private String errorMessage;
    private Integer tokenInput;
    private Integer tokenOutput;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
