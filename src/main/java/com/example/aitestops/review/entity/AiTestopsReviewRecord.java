package com.example.aitestops.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 人工评审记录实体，保存编辑、确认、驳回等人工操作。
 */
@Data
@TableName("ai_testops_review_record")
public class AiTestopsReviewRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
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
