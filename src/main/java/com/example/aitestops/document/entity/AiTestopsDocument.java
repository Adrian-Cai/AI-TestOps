package com.example.aitestops.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档主表实体，保存文本输入或上传文件的基础信息与解析状态。
 */
@Data
@TableName("ai_testops_document")
public class AiTestopsDocument {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String documentId;
    private String title;
    private String sourceType;
    private String fileName;
    private String fileType;
    private String filePath;
    private Long fileSize;
    private String fileHash;
    private String duplicateDocumentId;
    private String rawText;
    private String metadataJson;
    private String parseStatus;
    private String parseError;
    private LocalDateTime uploadedAt;
    private LocalDateTime parsedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
