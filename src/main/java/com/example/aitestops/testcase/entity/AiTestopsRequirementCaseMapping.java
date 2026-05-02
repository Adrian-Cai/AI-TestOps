package com.example.aitestops.testcase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 需求与测试用例映射实体，用于后续统计需求覆盖率。
 */
@Data
@TableName("ai_testops_requirement_case_mapping")
public class AiTestopsRequirementCaseMapping {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String mappingId;
    private String requirementId;
    private String testCaseId;
    private String draftCaseId;
    private String generationId;
    private String documentId;
    private LocalDateTime createdAt;
}
