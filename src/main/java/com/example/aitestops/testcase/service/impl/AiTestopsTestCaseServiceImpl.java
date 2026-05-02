package com.example.aitestops.testcase.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.aitestops.testcase.entity.AiTestopsTestCase;
import com.example.aitestops.testcase.mapper.AiTestopsTestCaseMapper;
import com.example.aitestops.testcase.service.AiTestopsTestCaseService;
import com.example.aitestops.testcase.vo.TestCaseVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 正式测试用例服务实现。
 */
@Slf4j
@Service
public class AiTestopsTestCaseServiceImpl
        extends ServiceImpl<AiTestopsTestCaseMapper, AiTestopsTestCase>
        implements AiTestopsTestCaseService {

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
        TestCaseVO vo = new TestCaseVO();
        vo.setTestCaseId(testCase.getTestCaseId());
        vo.setSourceDraftCaseId(testCase.getSourceDraftCaseId());
        vo.setCaseId(testCase.getCaseId());
        vo.setGenerationId(testCase.getGenerationId());
        vo.setDocumentId(testCase.getDocumentId());
        vo.setRequirementExtractId(testCase.getRequirementExtractId());
        vo.setTitle(testCase.getTitle());
        vo.setPreconditionsJson(testCase.getPreconditionsJson());
        vo.setStepsJson(testCase.getStepsJson());
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
}
