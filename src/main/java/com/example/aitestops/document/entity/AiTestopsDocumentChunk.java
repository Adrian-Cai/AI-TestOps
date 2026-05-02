package com.example.aitestops.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档分块实体，保存清洗后的 chunk 文本和估算 token 数。
 */
@Data
@TableName("ai_testops_document_chunk")
public class AiTestopsDocumentChunk {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String chunkId;
    private String documentId;
    private Integer chunkIndex;
    private String sectionTitle;
    private String chunkText;
    private Integer tokenCount;
    private String extraJson;
    private LocalDateTime createdAt;
}
