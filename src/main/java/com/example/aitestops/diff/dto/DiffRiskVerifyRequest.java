package com.example.aitestops.diff.dto;

import lombok.Data;

import java.util.List;

@Data
public class DiffRiskVerifyRequest {

    private String verifyResult;
    private List<Long> relatedCaseIds;
    private String remark;
    private String operator;
}
