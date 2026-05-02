package com.example.aitestops.testcase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 正式测试用例实体，保存人工确认后的测试用例。
 */
@Data
@TableName("ai_testops_test_case")
public class AiTestopsTestCase {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String testCaseId;
    private String sourceDraftCaseId;
    private String caseId;
    private String generationId;
    private String documentId;
    private String requirementExtractId;
    private String title;
    private String preconditionsJson;
    private String stepsJson;
    private String priority;
    private String caseType;
    private String riskLevel;
    private String requirementRefsJson;
    private String riskTagsJson;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
