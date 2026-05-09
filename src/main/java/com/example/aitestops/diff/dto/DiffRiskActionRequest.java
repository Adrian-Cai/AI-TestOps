package com.example.aitestops.diff.dto;

import lombok.Data;

import java.util.List;

@Data
public class DiffRiskActionRequest {

    private String actionType;
    private String actionDesc;
    private String operator;
    private String ignoreReason;
    private List<Long> relatedCaseIds;
}
