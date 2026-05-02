package com.example.aitestops.parser.service;

import com.example.aitestops.parser.dto.ChunkData;
import com.example.aitestops.parser.dto.DocumentSection;

import java.util.List;

/**
 * 文档结构化切分服务接口。
 */
public interface DocumentChunkService {

    List<DocumentSection> splitSections(String cleanedText);

    List<ChunkData> splitChunks(String cleanedText);
}
