package com.example.aitestops.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 文本需求建档请求。
 */
@Data
public class TextDocumentCreateRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 255, message = "标题不能超过255个字符")
    private String title;

    @NotBlank(message = "需求文本内容不能为空")
    private String content;
}
