package com.example.aitestops.parser.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档分块中间对象。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChunkData {

    private String chunkId;
    private Integer chunkIndex;
    private String sectionTitle;
    private String chunkText;
    private Integer tokenCount;
}
