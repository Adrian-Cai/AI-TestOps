package com.example.aitestops.ai.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 校验结果响应 VO。
 */
@Data
public class ValidationResultVO {

    private String validationId;
    private String generationId;
    private String validationType;
    private String status;
    private String errorDetailJson;
    private String warningDetailJson;
    private LocalDateTime createdAt;
}
