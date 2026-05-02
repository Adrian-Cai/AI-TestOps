package com.example.aitestops.export.service;

/**
 * 测试用例导出服务。
 */
public interface TestCaseExportService {

    byte[] exportJson(String documentId, String requirementExtractId);

    byte[] exportExcel(String documentId, String requirementExtractId);
}
