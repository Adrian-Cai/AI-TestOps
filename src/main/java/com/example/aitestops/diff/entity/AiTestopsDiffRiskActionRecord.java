package com.example.aitestops.diff.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Diff 风险处理记录实体。
 * <p>
 * 记录风险项的处理动作、状态变更和操作人信息。
 * </p>
 */
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
