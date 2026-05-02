package com.example.aitestops.parser.chunk;

import com.example.aitestops.common.util.IdGenerator;
import com.example.aitestops.parser.dto.ChunkData;
import com.example.aitestops.parser.dto.DocumentSection;
import com.example.aitestops.parser.dto.ParserProperties;
import com.example.aitestops.parser.service.DocumentChunkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 第一阶段文档切分实现，按段落聚合并控制 chunk 长度。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentChunkServiceImpl implements DocumentChunkService {

    private final ParserProperties parserProperties;

    @Override
    public List<DocumentSection> splitSections(String cleanedText) {
        List<DocumentSection> sections = new ArrayList<>();
        if (!StringUtils.hasText(cleanedText)) {
            return sections;
        }

        String[] paragraphs = cleanedText.split("\\n\\s*\\n");
        int sectionIndex = 1;
        for (String paragraph : paragraphs) {
            String content = paragraph.trim();
            if (!StringUtils.hasText(content)) {
                continue;
            }
            String title = "Section " + sectionIndex;
            sections.add(new DocumentSection(sectionIndex, title, content));
            sectionIndex++;
        }
        log.info("sections 切分完成: sectionCount={}", sections.size());
        return sections;
    }

    @Override
    public List<ChunkData> splitChunks(String cleanedText) {
        List<ChunkData> chunks = new ArrayList<>();
        if (!StringUtils.hasText(cleanedText)) {
            return chunks;
        }

        String[] paragraphs = cleanedText.split("\\n\\s*\\n");
        StringBuilder current = new StringBuilder();
        int chunkIndex = 1;
        for (String paragraph : paragraphs) {
            String normalized = paragraph.trim();
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            if (current.length() > 0 && current.length() + normalized.length() + 2 > parserProperties.getChunkTargetSize()) {
                chunkIndex = addChunk(chunks, current.toString(), chunkIndex);
                current.setLength(0);
            }
            appendWithHardLimit(chunks, current, normalized, chunkIndex);
            chunkIndex = chunks.size() + 1;
        }
        if (current.length() > 0) {
            addChunk(chunks, current.toString(), chunkIndex);
        }

        log.info("chunks 切分完成: chunkCount={}", chunks.size());
        return chunks;
    }

    private void appendWithHardLimit(List<ChunkData> chunks, StringBuilder current, String text, int chunkIndex) {
        int maxSize = parserProperties.getChunkMaxSize();
        String remaining = text;
        while (remaining.length() > maxSize) {
            if (current.length() > 0) {
                addChunk(chunks, current.toString(), chunkIndex);
                current.setLength(0);
                chunkIndex = chunks.size() + 1;
            }
            String slice = remaining.substring(0, maxSize);
            addChunk(chunks, slice, chunkIndex);
            remaining = remaining.substring(maxSize);
            chunkIndex = chunks.size() + 1;
        }
        if (current.length() > 0) {
            current.append("\n\n");
        }
        current.append(remaining);
    }

    private int addChunk(List<ChunkData> chunks, String text, int chunkIndex) {
        String chunkText = text.trim();
        if (!StringUtils.hasText(chunkText)) {
            return chunkIndex;
        }
        int tokenCount = Math.max(1, (int) Math.ceil(chunkText.length() / 4.0));
        chunks.add(new ChunkData(IdGenerator.chunkId(), chunkIndex, "Section " + chunkIndex, chunkText, tokenCount));
        return chunkIndex + 1;
    }
}
