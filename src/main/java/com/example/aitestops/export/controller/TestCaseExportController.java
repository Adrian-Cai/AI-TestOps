package com.example.aitestops.export.controller;

import com.example.aitestops.export.service.TestCaseExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 测试用例导出 REST API。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai-testops/export/testcases")
@Tag(name = "AI测试设计-导出")
public class TestCaseExportController {

    private final TestCaseExportService exportService;

    @GetMapping("/json")
    @Operation(summary = "导出正式测试用例 JSON")
    public ResponseEntity<byte[]> exportJson(@RequestParam(required = false) String documentId,
                                             @RequestParam(required = false) String requirementExtractId) {
        return ResponseEntity.ok()
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment("ai-testcases.json"))
                .body(exportService.exportJson(documentId, requirementExtractId));
    }

    @GetMapping("/excel")
    @Operation(summary = "导出正式测试用例 Excel")
    public ResponseEntity<byte[]> exportExcel(@RequestParam(required = false) String documentId,
                                              @RequestParam(required = false) String requirementExtractId) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment("ai-testcases.xlsx"))
                .body(exportService.exportExcel(documentId, requirementExtractId));
    }

    private String attachment(String filename) {
        return ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build()
                .toString();
    }
}
