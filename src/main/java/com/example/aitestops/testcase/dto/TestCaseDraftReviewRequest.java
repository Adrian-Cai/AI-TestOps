package com.example.aitestops.testcase.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 测试用例草稿确认或驳回请求。
 */
@Data
public class TestCaseDraftReviewRequest {

    @Size(max = 500, message = "原因不能超过500个字符")
    private String reason;

    @Size(max = 64, message = "评审人不能超过64个字符")
    private String reviewer;
}
