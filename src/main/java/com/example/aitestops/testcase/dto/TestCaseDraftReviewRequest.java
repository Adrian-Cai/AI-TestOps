package com.example.aitestops.testcase.dto;

import lombok.Data;

/**
 * 测试用例草稿确认或驳回请求。
 */
@Data
public class TestCaseDraftReviewRequest {

    private String reason;
    private String reviewer;
}
