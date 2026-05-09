package com.example.aitestops.diff.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_testops_diff_risk_action_record")
public class AiTestopsDiffRiskActionRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long riskId;
    private Long taskId;
    private String actionType;
    private String beforeStatus;
    private String afterStatus;
    private String actionDesc;
    private String actionPayload;
    private String operator;
    private LocalDateTime createdAt;
}
