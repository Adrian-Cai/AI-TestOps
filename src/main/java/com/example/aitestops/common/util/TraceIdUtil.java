package com.example.aitestops.common.util;

import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 请求链路标识工具，用于把压测中的错误响应和服务端日志关联起来。
 */
public final class TraceIdUtil {

    public static final String MDC_KEY = "traceId";
    public static final String HEADER_NAME = "X-Trace-Id";

    private static final DateTimeFormatter TRACE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final int MAX_EXTERNAL_TRACE_ID_LENGTH = 128;

    private TraceIdUtil() {
    }

    public static String newTraceId() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return "TRACE_%s_%s".formatted(LocalDateTime.now().format(TRACE_TIME_FORMATTER), suffix);
    }

    public static String ensureTraceId() {
        String traceId = currentTraceId();
        if (!StringUtils.hasText(traceId)) {
            traceId = newTraceId();
            MDC.put(MDC_KEY, traceId);
        }
        return traceId;
    }

    public static String currentTraceIdOrDefault() {
        String traceId = currentTraceId();
        return StringUtils.hasText(traceId) ? traceId : "-";
    }

    public static String normalizeExternalTraceId(String traceId) {
        if (!StringUtils.hasText(traceId)) {
            return newTraceId();
        }
        String normalized = traceId.trim();
        if (normalized.length() > MAX_EXTERNAL_TRACE_ID_LENGTH || containsControlCharacter(normalized)) {
            return newTraceId();
        }
        return normalized;
    }

    private static boolean containsControlCharacter(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }

    private static String currentTraceId() {
        return MDC.get(MDC_KEY);
    }
}
