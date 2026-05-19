package com.example.aitestops.parser.tika;

import com.example.aitestops.common.exception.BusinessException;
import com.example.aitestops.common.exception.ErrorCode;
import com.example.aitestops.parser.dto.ParsedDocument;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class TikaParseServiceImplTest {

    @Test
    void parseShouldReturnRawTextAndNormalizedMetadata() throws Exception {
        AutoDetectParser parser = mock(AutoDetectParser.class);
        doAnswer(invocation -> {
            ContentHandler handler = invocation.getArgument(1);
            Metadata metadata = invocation.getArgument(2);
            metadata.set("single", "one");
            metadata.add("multi", "first");
            metadata.add("multi", "second");
            char[] content = "hello tika".toCharArray();
            handler.characters(content, 0, content.length);
            return null;
        }).when(parser).parse(any(), any(ContentHandler.class), any(Metadata.class), any(ParseContext.class));
        Path tempFile = Files.createTempFile("ai-testops", ".txt");
        Files.writeString(tempFile, "ignored by mock parser");

        ParsedDocument result = new TikaParseServiceImpl(parser).parse(tempFile, "demo.txt");

        assertThat(result.getRawText()).isEqualTo("hello tika");
        assertThat(result.getMetadata()).containsEntry("single", "one");
        assertThat(result.getMetadata().get("multi")).isInstanceOf(String[].class);
        assertThat((String[]) result.getMetadata().get("multi")).containsExactly("first", "second");
    }

    @Test
    void parseShouldWrapParserFailureAsBusinessException() throws Exception {
        AutoDetectParser parser = mock(AutoDetectParser.class);
        doThrow(new IllegalStateException("parse failed"))
                .when(parser).parse(any(), any(ContentHandler.class), any(Metadata.class), any(ParseContext.class));
        Path tempFile = Files.createTempFile("ai-testops", ".txt");
        Files.writeString(tempFile, "bad content");

        assertThatThrownBy(() -> new TikaParseServiceImpl(parser).parse(tempFile, "bad.txt"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Tika 解析失败")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(ErrorCode.TIKA_PARSE_FAILED.getCode()));
    }
}
