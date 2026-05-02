package com.example.aitestops.parser;

import com.example.aitestops.parser.service.impl.TextCleanServiceImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextCleanServiceImplTest {

    private final TextCleanServiceImpl textCleanService = new TextCleanServiceImpl();

    @Test
    void cleanShouldNormalizeWhitespaceAndControlChars() {
        String cleaned = textCleanService.clean(" 第一行\u0000  内容\r\n\r\n\r\n第二行\t内容 ");

        assertThat(cleaned).isEqualTo("第一行 内容\n\n第二行 内容");
    }
}
