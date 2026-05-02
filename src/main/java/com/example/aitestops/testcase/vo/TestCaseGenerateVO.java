package com.example.aitestops.testcase.vo;

import lombok.Data;

import java.util.List;

/**
 * 测试用例生成响应 VO。
 */
@Data
public class TestCaseGenerateVO {

    private String generationId;
    private String documentId;
    private String requirementExtractId;
    private String validationStatus;
    private Integer draftCount;
    private List<TestCaseDraftVO> drafts;
}
