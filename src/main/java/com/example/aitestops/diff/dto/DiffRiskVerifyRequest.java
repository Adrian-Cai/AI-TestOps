package com.example.aitestops.diff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class DiffRiskVerifyRequest {

    @NotBlank(message = "验证结果不能为空")
    @Pattern(regexp = "PASS|FAIL|BLOCKED", message = "验证结果只能使用 PASS、FAIL、BLOCKED")
    private String verifyResult;

    private List<Long> relatedCaseIds;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;

    @NotBlank(message = "操作人不能为空")
    @Size(max = 64, message = "操作人不能超过64个字符")
    private String operator;
}
