package com.example.aitestops.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Prompt 模板实体，保存需求解析、用例生成等阶段的提示词。
 */
@Data
@TableName("ai_testops_prompt_template")
public class AiTestopsPromptTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String templateCode;
    private String templateName;
    private String templateType;
    private String version;
    private String promptContent;
    private String jsonSchema;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
