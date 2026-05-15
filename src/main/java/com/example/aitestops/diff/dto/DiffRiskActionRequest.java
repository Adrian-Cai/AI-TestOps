package com.example.aitestops.diff.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class DiffRiskActionRequest {

    @NotBlank(message = "动作类型不能为空")
    @Pattern(regexp = "CONFIRM|IGNORE|LINK_CASE|MARK_PASS|MARK_FAIL|MARK_BLOCKED|CLOSE",
            message = "不支持的风险动作类型")
    private String actionType;

    @Size(max = 500, message = "动作描述不能超过500个字符")
    private String actionDesc;

    @NotBlank(message = "操作人不能为空")
    @Size(max = 64, message = "操作人不能超过64个字符")
    private String operator;

    @Size(max = 500, message = "忽略原因不能超过500个字符")
    private String ignoreReason;

    private List<Long> relatedCaseIds;

    @AssertTrue(message = "IGNORE 动作必须填写忽略原因")
    public boolean isIgnoreReasonValid() {
        return !"IGNORE".equals(actionType) || (ignoreReason != null && !ignoreReason.isBlank());
    }

    @AssertTrue(message = "LINK_CASE 动作必须提供关联用例")
    public boolean isRelatedCasesValid() {
        return !"LINK_CASE".equals(actionType) || (relatedCaseIds != null && !relatedCaseIds.isEmpty());
    }
}
