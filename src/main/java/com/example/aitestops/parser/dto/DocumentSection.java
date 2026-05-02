package com.example.aitestops.parser.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档章节结构，第一阶段按段落聚合生成。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentSection {

    private Integer sectionIndex;
    private String sectionTitle;
    private String content;
}
