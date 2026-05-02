package com.example.aitestops.export.service.impl;

import com.example.aitestops.common.exception.BusinessException;
import com.example.aitestops.common.exception.ErrorCode;
import com.example.aitestops.common.util.JsonUtil;
import com.example.aitestops.export.service.TestCaseExportService;
import com.example.aitestops.testcase.service.AiTestopsTestCaseService;
import com.example.aitestops.testcase.vo.TestCaseVO;
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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 测试用例导出服务实现，支持 JSON 和 Excel。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestCaseExportServiceImpl implements TestCaseExportService {

    private static final String[] HEADERS = {
            "testCaseId", "caseId", "title", "priority", "caseType", "riskLevel",
            "requirementRefsJson", "preconditionsJson", "stepsJson", "riskTagsJson"
    };

    private final AiTestopsTestCaseService testCaseService;
    private final ObjectMapper objectMapper;

    @Override
    public byte[] exportJson(String documentId, String requirementExtractId) {
        List<TestCaseVO> cases = testCaseService.listCases(documentId, requirementExtractId);
        log.info("导出正式测试用例 JSON: documentId={}, requirementExtractId={}, count={}",
                documentId, requirementExtractId, cases.size());
        return JsonUtil.toJson(objectMapper, cases).getBytes(StandardCharsets.UTF_8);
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

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            for (int i = 0; i < cases.size(); i++) {
                writeCaseRow(sheet.createRow(i + 1), cases.get(i));
            }
            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "导出 Excel 失败: " + ex.getMessage(), ex);
        }
    }

    private void writeCaseRow(Row row, TestCaseVO testCase) {
        writeCell(row, 0, testCase.getTestCaseId());
        writeCell(row, 1, testCase.getCaseId());
        writeCell(row, 2, testCase.getTitle());
        writeCell(row, 3, testCase.getPriority());
        writeCell(row, 4, testCase.getCaseType());
        writeCell(row, 5, testCase.getRiskLevel());
        writeCell(row, 6, testCase.getRequirementRefsJson());
        writeCell(row, 7, testCase.getPreconditionsJson());
        writeCell(row, 8, testCase.getStepsJson());
        writeCell(row, 9, testCase.getRiskTagsJson());
    }

    private void writeCell(Row row, int index, String value) {
        row.createCell(index).setCellValue(value == null ? "" : value);
    }
}
