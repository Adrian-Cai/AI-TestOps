package com.example.aitestops.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request for creating a new Prompt template version.
 */
@Data
public class PromptTemplateVersionCreateRequest {

    @NotBlank(message = "templateCode 不能为空")
    @Size(max = 64, message = "templateCode 不能超过64个字符")
    private String templateCode;

    @NotBlank(message = "templateName 不能为空")
    @Size(max = 128, message = "templateName 不能超过128个字符")
    private String templateName;

    @NotBlank(message = "templateType 不能为空")
    @Size(max = 64, message = "templateType 不能超过64个字符")
    private String templateType;

    @NotBlank(message = "version 不能为空")
    @Size(max = 32, message = "version 不能超过32个字符")
    private String version;

    @NotBlank(message = "promptContent 不能为空")
    private String promptContent;

    private String jsonSchema;

    private Boolean enabled = false;
}
