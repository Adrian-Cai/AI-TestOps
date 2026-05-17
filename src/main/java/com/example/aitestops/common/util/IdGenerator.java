package com.example.aitestops.common.util;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 业务 ID 生成工具，使用业务前缀、日期和随机数保证 Demo 阶段可读。
 */
public final class IdGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
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

    private static String next(String prefix) {
        int random = RANDOM.nextInt(1_000_000);
        return "%s_%s_%06d".formatted(prefix, LocalDate.now().format(DATE_FORMATTER), random);
    }
}
