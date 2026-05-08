package com.example.aitestops.diff.coverage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aitestops.diff.entity.AiTestopsDiffRiskCaseRel;
import com.example.aitestops.diff.entity.AiTestopsDiffRiskItem;
import com.example.aitestops.diff.enums.DiffCoverageStatusEnum;
import com.example.aitestops.testcase.entity.AiTestopsTestCase;
import com.example.aitestops.testcase.service.AiTestopsTestCaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DiffCoverageMatcher {

    private static final BigDecimal COVERED_THRESHOLD = new BigDecimal("0.5500");
    private static final BigDecimal PARTIAL_THRESHOLD = new BigDecimal("0.2500");

    private final AiTestopsTestCaseService testCaseService;

    public CoverageMatchResult match(AiTestopsDiffRiskItem risk) {
        List<AiTestopsTestCase> cases = loadCandidateCases(risk);
        if (cases.isEmpty()) {
            return new CoverageMatchResult(DiffCoverageStatusEnum.NOT_COVERED.name(), "当前需求暂无可用于覆盖匹配的正式测试用例", List.of());
        }

        List<AiTestopsDiffRiskCaseRel> relations = new ArrayList<>();
        BigDecimal bestScore = BigDecimal.ZERO;
        for (AiTestopsTestCase testCase : cases) {
            BigDecimal score = score(risk, testCase);
            if (score.compareTo(bestScore) > 0) {
                bestScore = score;
            }
            if (score.compareTo(PARTIAL_THRESHOLD) >= 0) {
                AiTestopsDiffRiskCaseRel rel = new AiTestopsDiffRiskCaseRel();
                rel.setRiskId(risk.getId());
                rel.setCaseId(testCase.getId());
                rel.setDocumentId(risk.getDocumentId());
                rel.setRequirementExtractId(risk.getRequirementExtractId());
                rel.setCaseSourceType("REQUIREMENT_GENERATED");
                rel.setRelationType("EXISTING_REQUIREMENT_CASE");
                rel.setCoverageJudgement(score.compareTo(COVERED_THRESHOLD) >= 0 ? "COVERED" : "PARTIAL");
                rel.setJudgementReason(buildJudgementReason(score, testCase));
                rel.setSimilarityScore(score);
                rel.setGeneratedFromAi(0);
                rel.setCreatedAt(LocalDateTime.now());
                relations.add(rel);
            }
        }

        if (bestScore.compareTo(COVERED_THRESHOLD) >= 0) {
            return new CoverageMatchResult(DiffCoverageStatusEnum.COVERED.name(), "已有用例与风险关键词匹配度较高", relations);
        }
        if (bestScore.compareTo(PARTIAL_THRESHOLD) >= 0) {
            return new CoverageMatchResult(DiffCoverageStatusEnum.PARTIAL_COVERED.name(), "已有用例覆盖部分风险关键词，仍需补充缺失场景", relations);
        }
        return new CoverageMatchResult(DiffCoverageStatusEnum.NOT_COVERED.name(), "未找到与风险场景明显匹配的正式测试用例", List.of());
    }

    private List<AiTestopsTestCase> loadCandidateCases(AiTestopsDiffRiskItem risk) {
        LambdaQueryWrapper<AiTestopsTestCase> wrapper = new LambdaQueryWrapper<AiTestopsTestCase>()
                .eq(AiTestopsTestCase::getStatus, "ACTIVE")
                .orderByDesc(AiTestopsTestCase::getCreatedAt);
        if (StringUtils.hasText(risk.getRequirementExtractId())) {
            wrapper.eq(AiTestopsTestCase::getRequirementExtractId, risk.getRequirementExtractId());
        } else if (StringUtils.hasText(risk.getDocumentId())) {
            wrapper.eq(AiTestopsTestCase::getDocumentId, risk.getDocumentId());
        }
        return testCaseService.list(wrapper);
    }

    private BigDecimal score(AiTestopsDiffRiskItem risk, AiTestopsTestCase testCase) {
        Set<String> riskTokens = tokenize(String.join(" ",
                value(risk.getRiskTitle()),
                value(risk.getRiskCategory()),
                value(risk.getAffectedModule()),
                value(risk.getAffectedScenarios()),
                value(risk.getRiskReason()),
                value(risk.getTestSuggestion()),
                value(risk.getMissingTestScenarios())));
        Set<String> caseTokens = tokenize(String.join(" ",
                value(testCase.getTitle()),
                value(testCase.getPreconditionsJson()),
                value(testCase.getStepsJson()),
                value(testCase.getExpectedResultsJson()),
                value(testCase.getRequirementRefsJson()),
                value(testCase.getRiskTagsJson())));
        if (riskTokens.isEmpty() || caseTokens.isEmpty()) {
            return BigDecimal.ZERO;
        }
        int hit = 0;
        for (String token : riskTokens) {
            if (caseTokens.contains(token)) {
                hit++;
            }
        }
        return BigDecimal.valueOf(hit)
                .divide(BigDecimal.valueOf(Math.max(1, riskTokens.size())), 4, RoundingMode.HALF_UP);
    }

    private Set<String> tokenize(String value) {
        String normalized = value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\[\\]{}\"':,./\\\\()_\\-]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : normalized.split(" ")) {
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private String buildJudgementReason(BigDecimal score, AiTestopsTestCase testCase) {
        return "匹配用例 %s，关键词匹配分 %.4f".formatted(testCase.getCaseId(), score);
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    public record CoverageMatchResult(String coverageStatus, String coverageReason, List<AiTestopsDiffRiskCaseRel> relations) {
    }
}
