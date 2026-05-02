package com.example.aitestops.parser.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Tika 解析后的原始结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParsedDocument {

    private String rawText;
    private Map<String, Object> metadata;
}
