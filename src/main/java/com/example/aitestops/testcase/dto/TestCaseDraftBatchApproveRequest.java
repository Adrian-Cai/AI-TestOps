package com.example.aitestops.testcase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量确认测试用例草稿请求。
 */
@Data
public class TestCaseDraftBatchApproveRequest {

    @NotEmpty(message = "草稿ID列表不能为空")
    private List<@NotBlank(message = "草稿ID不能为空") String> draftCaseIds;

    @NotBlank(message = "评审人不能为空")
    @Size(max = 64, message = "评审人不能超过64个字符")
    private String reviewer;
}
