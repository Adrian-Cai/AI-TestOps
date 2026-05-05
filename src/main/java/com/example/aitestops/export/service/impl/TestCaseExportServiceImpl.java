package com.example.aitestops.export.service.impl;

import com.example.aitestops.common.exception.BusinessException;
import com.example.aitestops.common.exception.ErrorCode;
import com.example.aitestops.common.util.JsonUtil;
import com.example.aitestops.export.service.TestCaseExportService;
import com.example.aitestops.export.vo.ExportTestCaseItem;
import com.example.aitestops.testcase.service.AiTestopsTestCaseService;
import com.example.aitestops.testcase.vo.TestCaseVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 测试用例导出服务实现，支持 JSON 和 Excel。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestCaseExportServiceImpl implements TestCaseExportService {

    private static final String[] EXCEL_HEADERS = {
            "用例标题", "优先级", "前置条件", "测试步骤", "预期结果", "风险标签"
    };

    private final AiTestopsTestCaseService testCaseService;
    private final ObjectMapper objectMapper;

    @Override
    public byte[] exportJson(String documentId, String requirementExtractId) {
        List<TestCaseVO> cases = testCaseService.listCases(documentId, requirementExtractId);
        List<ExportTestCaseItem> exportItems = cases.stream().map(this::toJsonExportItem).toList();
        log.info("导出正式测试用例 JSON: documentId={}, requirementExtractId={}, count={}",
                documentId, requirementExtractId, cases.size());
        return JsonUtil.toJson(objectMapper, exportItems).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] exportExcel(String documentId, String requirementExtractId) {
        List<TestCaseVO> cases = testCaseService.listCases(documentId, requirementExtractId);
        log.info("导出正式测试用例 Excel: documentId={}, requirementExtractId={}, count={}",
                documentId, requirementExtractId, cases.size());
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("test_cases");
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            CellStyle contentStyle = workbook.createCellStyle();
            contentStyle.setWrapText(true);

            Row header = sheet.createRow(0);
            for (int i = 0; i < EXCEL_HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(EXCEL_HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            for (int i = 0; i < cases.size(); i++) {
                writeCaseRow(sheet.createRow(i + 1), contentStyle, cases.get(i));
            }
            for (int i = 0; i < EXCEL_HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "导出 Excel 失败: " + ex.getMessage(), ex);
        }
    }

    private ExportTestCaseItem toJsonExportItem(TestCaseVO testCase) {
        StepParseResult steps = parseSteps(testCase.getStepsJson());
        return new ExportTestCaseItem(
                defaultString(testCase.getTitle()),
                defaultString(testCase.getPriority()),
                parseStringArrayForJson(testCase.getPreconditionsJson()),
                steps.testSteps(),
                steps.expectedResults(),
                parseStringArrayForJson(testCase.getRiskTagsJson())
        );
    }

    private void writeCaseRow(Row row, CellStyle contentStyle, TestCaseVO testCase) {
        StepParseResult steps = parseSteps(testCase.getStepsJson());
        writeCell(row, contentStyle, 0, testCase.getTitle());
        writeCell(row, contentStyle, 1, testCase.getPriority());
        writeCell(row, contentStyle, 2, formatStringArrayForExcel(testCase.getPreconditionsJson()));
        writeCell(row, contentStyle, 3, formatStepsForExcel(steps.testSteps(), steps.parsed(), steps.rawValue()));
        writeCell(row, contentStyle, 4, formatStepsForExcel(steps.expectedResults(), steps.parsed(), steps.parsed() ? null : ""));
        writeCell(row, contentStyle, 5, formatStringArrayForExcel(testCase.getRiskTagsJson()));
    }

    private List<String> parseStringArrayForJson(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isArray()) {
                return List.of(json);
            }
            List<String> items = new ArrayList<>();
            for (JsonNode item : node) {
                items.add(stringifyNode(item));
            }
            return items;
        } catch (Exception ex) {
            return List.of(json);
        }
    }

    private String formatStringArrayForExcel(String json) {
        if (!StringUtils.hasText(json)) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isArray()) {
                return json;
            }
            List<String> items = new ArrayList<>();
            for (JsonNode item : node) {
                items.add(stringifyNode(item));
            }
            return String.join("\n", items);
        } catch (Exception ex) {
            return json;
        }
    }

    private StepParseResult parseSteps(String stepsJson) {
        if (!StringUtils.hasText(stepsJson)) {
            return new StepParseResult(List.of(), List.of(), true, "");
        }
        try {
            JsonNode node = objectMapper.readTree(stepsJson);
            if (!node.isArray()) {
                return new StepParseResult(List.of(stepsJson), List.of(), false, stepsJson);
            }
            List<String> actions = new ArrayList<>();
            List<String> expectedResults = new ArrayList<>();
            for (JsonNode stepNode : node) {
                if (stepNode.isObject()) {
                    String action = textOrSerialized(stepNode.get("action"));
                    String expected = textOrSerialized(stepNode.get("expected_result"));
                    if (StringUtils.hasText(action)) {
                        actions.add(action);
                    }
                    if (StringUtils.hasText(expected)) {
                        expectedResults.add(expected);
                    }
                } else {
                    String raw = stringifyNode(stepNode);
                    if (StringUtils.hasText(raw)) {
                        actions.add(raw);
                    }
                }
            }
            return new StepParseResult(actions, expectedResults, true, "");
        } catch (Exception ex) {
            return new StepParseResult(List.of(stepsJson), List.of(), false, stepsJson);
        }
    }

    private String formatStepsForExcel(List<String> steps, boolean parsed, String rawValue) {
        if (!parsed) {
            return rawValue == null ? "" : rawValue;
        }
        if (steps.isEmpty()) {
            return rawValue == null ? "" : rawValue;
        }
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            lines.add((i + 1) + ". " + steps.get(i));
        }
        return String.join("\n", lines);
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String stringifyNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        return node.isTextual() ? node.asText() : node.toString();
    }

    private String textOrSerialized(JsonNode node) {
        String value = stringifyNode(node);
        return StringUtils.hasText(value) ? value : "";
    }

    private void writeCell(Row row, CellStyle style, int index, String value) {
        Cell cell = row.createCell(index);
        cell.setCellValue(defaultString(value));
        cell.setCellStyle(style);
    }

    private record StepParseResult(List<String> testSteps, List<String> expectedResults, boolean parsed, String rawValue) {
    }
}
