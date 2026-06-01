package com.example.aitestops.parser.service.impl;

import com.example.aitestops.parser.service.TextCleanService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 文本清洗实现，统一换行、空白和不可见控制字符。
 */
@Slf4j
@Service
public class TextCleanServiceImpl implements TextCleanService {

    @Override
    public String clean(String rawText) {
        if (rawText == null || !StringUtils.hasText(rawText)) {
            int beforeLength = rawText == null ? 0 : rawText.length();
            log.info("文本清洗完成: beforeLength={}, afterLength=0", beforeLength);
            return "";
        }

        String cleaned = rawText
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u00A0', ' ')
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();

        log.info("文本清洗完成: beforeLength={}, afterLength={}", beforeLength, cleaned.length());
        return cleaned;
    }
}
