package com.example.aitestops.review.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 人工评审记录响应 VO。
 */
@Data
public class ReviewRecordVO {

    private String reviewRecordId;
    private String caseId;
    private String draftCaseId;
    private String testCaseId;
    private String action;
    private String beforeJson;
    private String afterJson;
    private String reason;
    private String reviewer;
    private LocalDateTime createdAt;
}
