package com.example.aitestops.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 需求解析结果实体，保存 AI 从文档中提取出的结构化需求信息。
 */
@Data
@TableName("ai_testops_requirement_extract")
public class AiTestopsRequirementExtract {

    @TableId(type = IdType.AUTO)
    private Long id;
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
    private LocalDateTime updatedAt;
}
