package com.example.aitestops.export;

import com.example.aitestops.export.service.impl.TestCaseExportServiceImpl;
import com.example.aitestops.testcase.service.AiTestopsTestCaseService;
import com.example.aitestops.testcase.vo.TestCaseVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestCaseExportServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AiTestopsTestCaseService testCaseService;

    private TestCaseExportServiceImpl exportService;

    @BeforeEach
    void setUp() {
        exportService = new TestCaseExportServiceImpl(testCaseService, objectMapper);
    }

    @Test
    void exportJsonShouldOnlyIncludeKeyFieldsWithStructuredValues() throws Exception {
        when(testCaseService.listCases("DOC_001", null)).thenReturn(List.of(buildCase()));

        byte[] bytes = exportService.exportJson("DOC_001", null);

        JsonNode root = objectMapper.readTree(bytes);
        JsonNode first = root.get(0);
        assertThat(root).hasSize(1);
        assertThat(first.fieldNames()).toIterable()
                .containsExactly("title", "priority", "preconditions", "testSteps", "expectedResults", "riskTags");
        assertThat(first.get("title").asText()).isEqualTo("正常提交订单");
        assertThat(first.get("priority").asText()).isEqualTo("P1");
        assertThat(first.get("preconditions")).containsExactly(
                objectMapper.getNodeFactory().textNode("用户已登录"),
                objectMapper.getNodeFactory().textNode("商品库存充足")
        );
        assertThat(first.get("testSteps")).containsExactly(
                objectMapper.getNodeFactory().textNode("进入下单页面"),
                objectMapper.getNodeFactory().textNode("填写订单信息并提交")
        );
        assertThat(first.get("expectedResults")).containsExactly(
                objectMapper.getNodeFactory().textNode("页面正常展示"),
                objectMapper.getNodeFactory().textNode("订单提交成功")
        );
        assertThat(first.get("riskTags")).containsExactly(
                objectMapper.getNodeFactory().textNode("核心链路"),
                objectMapper.getNodeFactory().textNode("订单提交")
        );
        assertThat(first.has("testCaseId")).isFalse();
        assertThat(first.has("caseId")).isFalse();
        assertThat(first.has("riskLevel")).isFalse();
        assertThat(first.has("requirementRefsJson")).isFalse();
        assertThat(first.has("caseType")).isFalse();
    }

    @Test
    void exportExcelShouldUseChineseHeadersAndReadableMultilineFields() throws Exception {
        when(testCaseService.listCases("DOC_001", null)).thenReturn(List.of(buildCase()));

        byte[] bytes = exportService.exportExcel("DOC_001", null);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("用例标题");
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("优先级");
            assertThat(sheet.getRow(0).getCell(2).getStringCellValue()).isEqualTo("前置条件");
            assertThat(sheet.getRow(0).getCell(3).getStringCellValue()).isEqualTo("测试步骤");
            assertThat(sheet.getRow(0).getCell(4).getStringCellValue()).isEqualTo("预期结果");
            assertThat(sheet.getRow(0).getCell(5).getStringCellValue()).isEqualTo("风险标签");

            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("正常提交订单");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("P1");
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("用户已登录\n商品库存充足");
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("1. 进入下单页面\n2. 填写订单信息并提交");
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("1. 页面正常展示\n2. 订单提交成功");
            assertThat(sheet.getRow(1).getCell(5).getStringCellValue()).isEqualTo("核心链路\n订单提交");
        }
    }

    @Test
    void exportShouldFallbackWhenJsonFieldsAreInvalid() throws Exception {
        TestCaseVO broken = new TestCaseVO();
        broken.setTitle("异常数据导出");
        broken.setPriority("P2");
        broken.setPreconditionsJson("前置条件原文");
        broken.setStepsJson("步骤原文");
        broken.setRiskTagsJson("");
        when(testCaseService.listCases("DOC_002", null)).thenReturn(List.of(broken));

        byte[] jsonBytes = exportService.exportJson("DOC_002", null);
        JsonNode jsonRoot = objectMapper.readTree(jsonBytes).get(0);
        assertThat(jsonRoot.get("preconditions")).containsExactly(objectMapper.getNodeFactory().textNode("前置条件原文"));
        assertThat(jsonRoot.get("testSteps")).containsExactly(objectMapper.getNodeFactory().textNode("步骤原文"));
        assertThat(jsonRoot.get("expectedResults")).isEmpty();
        assertThat(jsonRoot.get("riskTags")).isEmpty();

        byte[] excelBytes = exportService.exportExcel("DOC_002", null);
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("前置条件原文");
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("步骤原文");
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEmpty();
            assertThat(sheet.getRow(1).getCell(5).getStringCellValue()).isEmpty();
        }
    }

    private TestCaseVO buildCase() {
        TestCaseVO testCase = new TestCaseVO();
        testCase.setTestCaseId("TCDB_001");
        testCase.setCaseId("TC_001");
        testCase.setTitle("正常提交订单");
        testCase.setPriority("P1");
        testCase.setCaseType("NORMAL");
        testCase.setRiskLevel("P0");
        testCase.setRequirementRefsJson("[\"REQ_001\",\"REQ_004\"]");
        testCase.setPreconditionsJson("[\"用户已登录\",\"商品库存充足\"]");
        testCase.setStepsJson("""
                [
                  {"step_no":1,"action":"进入下单页面","expected_result":"页面正常展示"},
                  {"step_no":2,"action":"填写订单信息并提交","expected_result":"订单提交成功"}
                ]
                """);
        testCase.setRiskTagsJson("[\"核心链路\",\"订单提交\"]");
        return testCase;
    }
}
