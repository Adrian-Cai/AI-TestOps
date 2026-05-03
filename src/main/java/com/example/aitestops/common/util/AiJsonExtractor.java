package com.example.aitestops.common.util;

import org.springframework.util.StringUtils;

/**
 * Extracts the first JSON object from LLM output.
 */
public final class AiJsonExtractor {

    private AiJsonExtractor() {
    }

    public static String extractJsonObject(String content) {
        if (!StringUtils.hasText(content)) {
            return content;
        }
        String trimmed = stripFence(content.trim());
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }

        int start = trimmed.indexOf('{');
        if (start < 0) {
            return trimmed;
        }

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return trimmed.substring(start, i + 1);
                }
            }
        }
        return trimmed;
    }

    private static String stripFence(String content) {
        if (!content.startsWith("```")) {
            return content;
        }
        int firstLineEnd = content.indexOf('\n');
        int lastFenceStart = content.lastIndexOf("```");
        if (firstLineEnd < 0 || lastFenceStart <= firstLineEnd) {
            return content;
        }
        return content.substring(firstLineEnd + 1, lastFenceStart).trim();
    }
}
