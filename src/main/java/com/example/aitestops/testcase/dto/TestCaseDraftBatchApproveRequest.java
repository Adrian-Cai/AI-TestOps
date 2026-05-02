package com.example.aitestops.testcase.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量确认测试用例草稿请求。
 */
@Data
public class TestCaseDraftBatchApproveRequest {

    private List<String> draftCaseIds;
    private String reviewer;
}
