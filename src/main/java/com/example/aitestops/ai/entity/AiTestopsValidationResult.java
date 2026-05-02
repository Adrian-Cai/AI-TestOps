package com.example.aitestops.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 校验结果实体，保存 AI 输出格式、必填字段和重复检测结果。
 */
@Data
@TableName("ai_testops_validation_result")
public class AiTestopsValidationResult {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String validationId;
    private String generationId;
    private String validationType;
    private String status;
    private String errorDetailJson;
    private String warningDetailJson;
    private LocalDateTime createdAt;
}
