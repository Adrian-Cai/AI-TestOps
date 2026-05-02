package com.example.aitestops.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 大模型生成记录实体，保存 prompt、输入快照、输出结果和生成状态。
 */
@Data
@TableName("ai_testops_generation_record")
public class AiTestopsGenerationRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
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
