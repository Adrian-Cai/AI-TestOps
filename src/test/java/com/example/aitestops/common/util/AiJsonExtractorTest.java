package com.example.aitestops.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiJsonExtractorTest {

    @Test
    void extractJsonObjectShouldReturnPlainJson() {
        assertThat(AiJsonExtractor.extractJsonObject("{\"status\":\"ok\"}"))
                .isEqualTo("{\"status\":\"ok\"}");
    }

    @Test
    void extractJsonObjectShouldStripMarkdownFence() {
        String content = """
                ```json
                {"status":"ok"}
                ```
                """;

        assertThat(AiJsonExtractor.extractJsonObject(content))
                .isEqualTo("{\"status\":\"ok\"}");
    }

    @Test
    void extractJsonObjectShouldIgnoreTextAroundJson() {
        String content = "下面是结果：{\"status\":\"ok\",\"text\":\"value with } brace\"} 请查收";

        assertThat(AiJsonExtractor.extractJsonObject(content))
                .isEqualTo("{\"status\":\"ok\",\"text\":\"value with } brace\"}");
    }
}
