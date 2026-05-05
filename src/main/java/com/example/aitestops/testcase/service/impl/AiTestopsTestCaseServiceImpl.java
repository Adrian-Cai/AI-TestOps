package com.example.aitestops.testcase.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.aitestops.common.util.JsonUtil;
import com.example.aitestops.testcase.entity.AiTestopsTestCase;
import com.example.aitestops.testcase.mapper.AiTestopsTestCaseMapper;
import com.example.aitestops.testcase.service.AiTestopsTestCaseService;
import com.example.aitestops.testcase.vo.TestCaseVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 正式测试用例服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiTestopsTestCaseServiceImpl
        extends ServiceImpl<AiTestopsTestCaseMapper, AiTestopsTestCase>
        implements AiTestopsTestCaseService {

    private final ObjectMapper objectMapper;

    @Override
    public List<TestCaseVO> listCases(String documentId, String requirementExtractId) {
        log.info("查询正式测试用例列表: documentId={}, requirementExtractId={}", documentId, requirementExtractId);
        LambdaQueryWrapper<AiTestopsTestCase> wrapper = new LambdaQueryWrapper<AiTestopsTestCase>()
                .orderByDesc(AiTestopsTestCase::getCreatedAt);
        if (StringUtils.hasText(documentId)) {
            wrapper.eq(AiTestopsTestCase::getDocumentId, documentId);
        }
        if (StringUtils.hasText(requirementExtractId)) {
            wrapper.eq(AiTestopsTestCase::getRequirementExtractId, requirementExtractId);
        }
        return list(wrapper).stream().map(this::toVO).toList();
    }

    private TestCaseVO toVO(AiTestopsTestCase testCase) {
        ViewCaseContent normalized = normalizeCaseContentForView(testCase.getStepsJson(), testCase.getExpectedResultsJson());
        TestCaseVO vo = new TestCaseVO();
        vo.setTestCaseId(testCase.getTestCaseId());
        vo.setSourceDraftCaseId(testCase.getSourceDraftCaseId());
        vo.setCaseId(testCase.getCaseId());
        vo.setGenerationId(testCase.getGenerationId());
        vo.setDocumentId(testCase.getDocumentId());
        vo.setRequirementExtractId(testCase.getRequirementExtractId());
        vo.setTitle(testCase.getTitle());
        vo.setPreconditionsJson(testCase.getPreconditionsJson());
        vo.setStepsJson(normalized.stepsJson());
        vo.setExpectedResultsJson(normalized.expectedResultsJson());
        vo.setPriority(testCase.getPriority());
        vo.setCaseType(testCase.getCaseType());
        vo.setRiskLevel(testCase.getRiskLevel());
        vo.setRequirementRefsJson(testCase.getRequirementRefsJson());
        vo.setRiskTagsJson(testCase.getRiskTagsJson());
        vo.setStatus(testCase.getStatus());
        vo.setCreatedAt(testCase.getCreatedAt());
        vo.setUpdatedAt(testCase.getUpdatedAt());
        return vo;
    }

    private ViewCaseContent normalizeCaseContentForView(String stepsJson, String expectedResultsJson) {
        String normalizedStepsJson = stepsJson == null ? "[]" : stepsJson;
        String normalizedExpectedResultsJson = StringUtils.hasText(expectedResultsJson) ? expectedResultsJson : "[]";
        try {
            JsonNode stepsNode = StringUtils.hasText(stepsJson) ? objectMapper.readTree(stepsJson) : objectMapper.createArrayNode();
            if (!stepsNode.isArray()) {
                return new ViewCaseContent(normalizedStepsJson, normalizedExpectedResultsJson);
            }
            List<ObjectNode> sanitizedSteps = new ArrayList<>();
            List<String> legacyExpectedResults = new ArrayList<>();
            for (JsonNode stepNode : stepsNode) {
                if (stepNode != null && stepNode.isObject()) {
                    ObjectNode sanitizedStep = ((ObjectNode) stepNode.deepCopy());
                    JsonNode legacyExpected = sanitizedStep.remove("expected_result");
                    if (legacyExpected != null && !legacyExpected.isNull() && StringUtils.hasText(legacyExpected.asText())) {
                        legacyExpectedResults.add(legacyExpected.asText());
                    }
                    sanitizedSteps.add(sanitizedStep);
                }
            }
            normalizedStepsJson = JsonUtil.toJson(objectMapper, sanitizedSteps);
            if (!StringUtils.hasText(expectedResultsJson) && !legacyExpectedResults.isEmpty()) {
                normalizedExpectedResultsJson = JsonUtil.toJson(objectMapper, legacyExpectedResults);
            }
        } catch (Exception ex) {
            log.debug("兼容解析正式用例步骤/预期结果失败，按原值返回", ex);
        }
        return new ViewCaseContent(normalizedStepsJson, normalizedExpectedResultsJson);
    }

    private record ViewCaseContent(String stepsJson, String expectedResultsJson) {
    }
}
