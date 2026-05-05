package com.example.aitestops.testcase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 测试用例草稿实体，保存 AI 生成但尚未人工确认的用例。
 */
@Data
@TableName("ai_testops_test_case_draft")
public class AiTestopsTestCaseDraft {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String draftCaseId;
    private String caseId;
    private String generationId;
    private String documentId;
    private String requirementExtractId;
    private String title;
    private String preconditionsJson;
    private String stepsJson;
    private String expectedResultsJson;
    private String priority;
    private String caseType;
    private String riskLevel;
    private String requirementRefsJson;
    private String riskTagsJson;
    private String reviewStatus;
    private String rawCaseJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
