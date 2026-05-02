package com.example.aitestops.parser;

import com.example.aitestops.parser.chunk.DocumentChunkServiceImpl;
import com.example.aitestops.parser.dto.ChunkData;
import com.example.aitestops.parser.dto.ParserProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentChunkServiceImplTest {

    @Test
    void splitChunksShouldCreateMultipleChunksByTargetSize() {
        ParserProperties properties = new ParserProperties();
        properties.setChunkTargetSize(20);
        properties.setChunkMaxSize(30);
        DocumentChunkServiceImpl service = new DocumentChunkServiceImpl(properties);

        List<ChunkData> chunks = service.splitChunks("第一段内容超过目标长度\n\n第二段内容也超过目标长度\n\n第三段");

        assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.getChunkId()).startsWith("CHUNK_"));
    }
}
