package com.example.aitestops.common.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 业务 ID 生成工具，使用业务前缀、日期和 UUID 后缀降低并发碰撞风险。
 */
public final class IdGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private IdGenerator() {
    }

    public static String documentId() {
        return next("DOC");
    }

    public static String chunkId() {
        return next("CHUNK");
    }

    public static String parseResultId() {
        return next("PARSE");
    }

    public static String generationId() {
        return next("GEN");
    }

    public static String requirementExtractId() {
        return next("REXT");
    }

    public static String draftCaseId() {
        return next("DRAFT");
    }

    public static String validationId() {
        return next("VAL");
    }

    public static String testCaseId() {
        return next("TCDB");
    }

    public static String mappingId() {
        return next("MAP");
    }

    public static String reviewRecordId() {
        return next("REV");
    }

    public static String diffRiskCode() {
        return next("RISK");
    }

    public static String diffTaskCode() {
        return next("DTASK");
    }

    public static String diffReportCode() {
        return next("DRPT");
    }

    private static String next(String prefix) {
        return "%s_%s_%s".formatted(prefix, LocalDate.now().format(DATE_FORMATTER), uuidSuffix());
    }

    private static String uuidSuffix() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
