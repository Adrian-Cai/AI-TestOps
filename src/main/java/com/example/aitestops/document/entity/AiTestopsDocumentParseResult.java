package com.example.aitestops.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档解析结果实体，保存完整解析结果 JSON 便于后续追溯。
 */
@Data
@TableName("ai_testops_document_parse_result")
public class AiTestopsDocumentParseResult {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String parseResultId;
    private String documentId;
    private String metadataJson;
    private String sectionsJson;
    private String chunksJson;
    private String parseResultJson;
    private Integer chunkCount;
    private Integer rawTextLength;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
