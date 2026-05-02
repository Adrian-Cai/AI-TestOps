package com.example.aitestops.parser.service;

import com.example.aitestops.parser.dto.ParsedDocument;

import java.nio.file.Path;

/**
 * Tika 文件解析服务接口。
 */
public interface TikaParseService {

    ParsedDocument parse(Path filePath, String fileName);
}
