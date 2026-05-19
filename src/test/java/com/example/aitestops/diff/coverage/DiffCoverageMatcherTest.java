package com.example.aitestops.diff.coverage;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.aitestops.diff.entity.AiTestopsDiffRiskCaseRel;
import com.example.aitestops.diff.entity.AiTestopsDiffRiskItem;
import com.example.aitestops.diff.enums.DiffCoverageStatusEnum;
import com.example.aitestops.testcase.entity.AiTestopsTestCase;
import com.example.aitestops.testcase.service.AiTestopsTestCaseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiffCoverageMatcherTest {

    @Mock
    private AiTestopsTestCaseService testCaseService;

    @Test
    void matchShouldReturnNotCoveredWhenThereAreNoCandidateCases() {
        when(testCaseService.list(anyWrapper())).thenReturn(List.of());
        DiffCoverageMatcher matcher = new DiffCoverageMatcher(testCaseService);

        DiffCoverageMatcher.CoverageMatchResult result = matcher.match(risk("订单提交库存不足风险", "DOC_1", "REQ_1"));

        assertThat(result.coverageStatus()).isEqualTo(DiffCoverageStatusEnum.NOT_COVERED.name());
        assertThat(result.coverageReason()).contains("暂无可用于覆盖匹配");
        assertThat(result.relations()).isEmpty();
    }

    @Test
    void matchShouldMarkCoveredWhenRiskAndCaseShareEnoughKeywords() {
        when(testCaseService.list(anyWrapper())).thenReturn(List.of(caseItem(
                7L,
                "TC_ORDER_STOCK",
                "订单提交库存不足校验",
                "[\"用户已登录\",\"商品库存不足\"]",
                "[\"提交订单\",\"校验库存不足提示\"]",
                "[\"提交失败并提示库存不足\"]",
                "[\"订单提交\",\"库存不足\"]"
        )));
        DiffCoverageMatcher matcher = new DiffCoverageMatcher(testCaseService);

        DiffCoverageMatcher.CoverageMatchResult result = matcher.match(highMatchRisk());

        assertThat(result.coverageStatus()).isEqualTo(DiffCoverageStatusEnum.COVERED.name());
        assertThat(result.coverageReason()).contains("匹配度较高");
        assertThat(result.relations()).hasSize(1);
        AiTestopsDiffRiskCaseRel relation = result.relations().get(0);
        assertThat(relation.getRiskId()).isEqualTo(100L);
        assertThat(relation.getCaseId()).isEqualTo(7L);
        assertThat(relation.getCoverageJudgement()).isEqualTo("COVERED");
        assertThat(relation.getSimilarityScore()).isGreaterThanOrEqualTo(new BigDecimal("0.5500"));
    }

    @Test
    void matchShouldCreateFallbackPartialRelationsWhenCasesExistButKeywordsDoNotMatch() {
        when(testCaseService.list(anyWrapper())).thenReturn(List.of(
                caseItem(1L, "TC_LOGIN", "用户登录成功", "[]", "[\"输入账号密码\"]", "[\"进入首页\"]", "[]"),
                caseItem(2L, "TC_PROFILE", "用户资料修改", "[]", "[\"保存昵称\"]", "[\"保存成功\"]", "[]")
        ));
        DiffCoverageMatcher matcher = new DiffCoverageMatcher(testCaseService);

        DiffCoverageMatcher.CoverageMatchResult result = matcher.match(risk("支付回调幂等风险", "DOC_1", "REQ_1"));

        assertThat(result.coverageStatus()).isEqualTo(DiffCoverageStatusEnum.PARTIAL_COVERED.name());
        assertThat(result.coverageReason()).contains("按部分覆盖处理");
        assertThat(result.relations()).hasSize(2);
        assertThat(result.relations())
                .allSatisfy(relation -> {
                    assertThat(relation.getCoverageJudgement()).isEqualTo("PARTIAL");
                    assertThat(relation.getRelationType()).isEqualTo("SAME_SOURCE_CANDIDATE_CASE");
                    assertThat(relation.getSimilarityScore()).isEqualByComparingTo("0.0000");
                });
    }

    @SuppressWarnings("unchecked")
    private Wrapper<AiTestopsTestCase> anyWrapper() {
        return any(Wrapper.class);
    }

    private AiTestopsDiffRiskItem risk(String title, String documentId, String requirementExtractId) {
        AiTestopsDiffRiskItem risk = new AiTestopsDiffRiskItem();
        risk.setId(100L);
        risk.setDocumentId(documentId);
        risk.setRequirementExtractId(requirementExtractId);
        risk.setRiskTitle(title);
        risk.setRiskCategory("业务逻辑");
        risk.setAffectedModule("src/main/java/com/example/aitestops/order/service/OrderSubmitService.java");
        risk.setAffectedScenarios("[\"订单提交\",\"库存校验\"]");
        risk.setRiskReason("Service 变更可能影响订单提交和库存不足提示");
        risk.setTestSuggestion("补充订单提交、库存不足、重复提交等回归验证");
        risk.setMissingTestScenarios("[\"库存不足\",\"重复提交\"]");
        return risk;
    }

    private AiTestopsDiffRiskItem highMatchRisk() {
        AiTestopsDiffRiskItem risk = new AiTestopsDiffRiskItem();
        risk.setId(100L);
        risk.setDocumentId("DOC_1");
        risk.setRequirementExtractId("REQ_1");
        risk.setRiskTitle("订单提交库存不足");
        risk.setRiskCategory("库存不足");
        risk.setAffectedModule("订单提交");
        risk.setAffectedScenarios("[\"库存不足\"]");
        risk.setRiskReason("库存不足");
        risk.setTestSuggestion("库存不足");
        risk.setMissingTestScenarios("[\"库存不足\"]");
        return risk;
    }

    private AiTestopsTestCase caseItem(
            long id,
            String caseId,
            String title,
            String preconditionsJson,
            String stepsJson,
            String expectedResultsJson,
            String riskTagsJson
    ) {
        AiTestopsTestCase testCase = new AiTestopsTestCase();
        testCase.setId(id);
        testCase.setCaseId(caseId);
        testCase.setTitle(title);
        testCase.setPreconditionsJson(preconditionsJson);
        testCase.setStepsJson(stepsJson);
        testCase.setExpectedResultsJson(expectedResultsJson);
        testCase.setRequirementRefsJson("[\"REQ_1\"]");
        testCase.setRiskTagsJson(riskTagsJson);
        return testCase;
    }
}
