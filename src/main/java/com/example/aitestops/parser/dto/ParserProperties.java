package com.example.aitestops.parser.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 文档解析配置，控制第一阶段 chunk 切分大小。
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ai-testops.parser")
public class ParserProperties {

    @Min(1)
    private int chunkTargetSize = 2000;

    @Min(1)
    private int chunkMaxSize = 3000;
}
