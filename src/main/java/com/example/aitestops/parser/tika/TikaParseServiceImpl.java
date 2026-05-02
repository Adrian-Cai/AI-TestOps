package com.example.aitestops.parser.tika;

import com.example.aitestops.common.exception.BusinessException;
import com.example.aitestops.common.exception.ErrorCode;
import com.example.aitestops.parser.dto.ParsedDocument;
import com.example.aitestops.parser.service.TikaParseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Apache Tika 解析实现，提取正文和元数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TikaParseServiceImpl implements TikaParseService {

    private final AutoDetectParser autoDetectParser;

    @Override
    public ParsedDocument parse(Path filePath, String fileName) {
        log.info("Tika 解析开始: fileName={}, path={}", fileName, filePath);
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        BodyContentHandler handler = new BodyContentHandler(-1);
        ParseContext parseContext = new ParseContext();

        try (TikaInputStream inputStream = TikaInputStream.get(filePath, metadata)) {
            autoDetectParser.parse(inputStream, handler, metadata, parseContext);
            Map<String, Object> metadataMap = normalizeMetadata(metadata);
            String rawText = handler.toString();
            log.info("Tika 解析结束: fileName={}, rawTextLength={}, metadataCount={}",
                    fileName, rawText == null ? 0 : rawText.length(), metadataMap.size());
            return new ParsedDocument(rawText, metadataMap);
        } catch (Exception ex) {
            log.error("Tika 解析失败: fileName={}, path={}", fileName, filePath, ex);
            throw new BusinessException(ErrorCode.TIKA_PARSE_FAILED, "Tika 解析失败: " + ex.getMessage(), ex);
        }
    }

    private Map<String, Object> normalizeMetadata(Metadata metadata) {
        Map<String, Object> metadataMap = new LinkedHashMap<>();
        for (String name : metadata.names()) {
            String[] values = metadata.getValues(name);
            if (values.length == 1) {
                metadataMap.put(name, values[0]);
            } else {
                metadataMap.put(name, values);
            }
        }
        return metadataMap;
    }
}
