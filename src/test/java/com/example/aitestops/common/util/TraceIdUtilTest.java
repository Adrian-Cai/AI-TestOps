package com.example.aitestops.common.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdUtilTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void newTraceIdShouldUseStablePrefixAndLength() {
        String traceId = TraceIdUtil.newTraceId();

        assertThat(traceId).startsWith("TRACE_");
        assertThat(traceId).hasSize("TRACE_yyyyMMddHHmmssSSS_".length() + 12);
    }

    @Test
    void ensureTraceIdShouldReuseExistingMdcValue() {
        MDC.put(TraceIdUtil.MDC_KEY, "external-trace");

        assertThat(TraceIdUtil.ensureTraceId()).isEqualTo("external-trace");
        assertThat(TraceIdUtil.currentTraceIdOrDefault()).isEqualTo("external-trace");
    }

    @Test
    void ensureTraceIdShouldCreateAndStoreMissingTraceId() {
        String traceId = TraceIdUtil.ensureTraceId();

        assertThat(traceId).startsWith("TRACE_");
        assertThat(MDC.get(TraceIdUtil.MDC_KEY)).isEqualTo(traceId);
    }

    @Test
    void currentTraceIdOrDefaultShouldReturnDashWhenAbsent() {
        assertThat(TraceIdUtil.currentTraceIdOrDefault()).isEqualTo("-");
    }

    @Test
    void normalizeExternalTraceIdShouldKeepSafeExternalValue() {
        assertThat(TraceIdUtil.normalizeExternalTraceId("  ci-trace-001  ")).isEqualTo("ci-trace-001");
    }

    @Test
    void normalizeExternalTraceIdShouldReplaceBlankLongOrControlCharacterValues() {
        assertThat(TraceIdUtil.normalizeExternalTraceId(null)).startsWith("TRACE_");
        assertThat(TraceIdUtil.normalizeExternalTraceId("x".repeat(129))).startsWith("TRACE_");
        assertThat(TraceIdUtil.normalizeExternalTraceId("bad\ntrace")).startsWith("TRACE_");
    }
}
