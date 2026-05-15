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
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DiffCoverageMatcher {

    private static final BigDecimal COVERED_THRESHOLD = new BigDecimal("0.5500");
    private static final BigDecimal PARTIAL_THRESHOLD = new BigDecimal("0.2000");
    private static final int FALLBACK_RELATION_LIMIT = 5;
    private static final Set<String> NOISE_TOKENS = new HashSet<>(Arrays.asList(
            "src", "main", "java", "com", "example", "aitestops", "file", "role",
            "rule", "risk", "diff", "modified", "added", "deleted", "renamed"
    ));

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
        return new CoverageMatchResult(DiffCoverageStatusEnum.NEED_CONFIRM.name(),
                "未找到明显关键词匹配，已关联同一需求来源的正式测试用例，请人工确认覆盖范围",
                buildFallbackRelations(risk, cases));
    }

    private List<AiTestopsDiffRiskCaseRel> buildFallbackRelations(AiTestopsDiffRiskItem risk, List<AiTestopsTestCase> cases) {
        return cases.stream()
                .limit(FALLBACK_RELATION_LIMIT)
                .map(testCase -> {
                    AiTestopsDiffRiskCaseRel rel = new AiTestopsDiffRiskCaseRel();
                    rel.setRiskId(risk.getId());
                    rel.setCaseId(testCase.getId());
                    rel.setDocumentId(risk.getDocumentId());
                    rel.setRequirementExtractId(risk.getRequirementExtractId());
                    rel.setCaseSourceType("REQUIREMENT_GENERATED");
                    rel.setRelationType("SAME_SOURCE_CANDIDATE_CASE");
                    rel.setCoverageJudgement(DiffCoverageStatusEnum.NEED_CONFIRM.name());
                    rel.setJudgementReason("同一需求来源已有正式用例，关键词未明显命中，请人工确认是否覆盖该风险场景: " + testCase.getCaseId());
                    rel.setSimilarityScore(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
                    rel.setGeneratedFromAi(0);
                    rel.setCreatedAt(LocalDateTime.now());
                    return rel;
                })
                .toList();
    }

    private List<AiTestopsTestCase> loadCandidateCases(AiTestopsDiffRiskItem risk) {
        LambdaQueryWrapper<AiTestopsTestCase> wrapper = new LambdaQueryWrapper<AiTestopsTestCase>()
                .eq(AiTestopsTestCase::getStatus, "ACTIVE")
                .orderByDesc(AiTestopsTestCase::getCreatedAt);
        if (StringUtils.hasText(risk.getRequirementExtractId()) && StringUtils.hasText(risk.getDocumentId())) {
            wrapper.and(item -> item
                    .eq(AiTestopsTestCase::getRequirementExtractId, risk.getRequirementExtractId())
                    .or()
                    .eq(AiTestopsTestCase::getDocumentId, risk.getDocumentId()));
        } else if (StringUtils.hasText(risk.getRequirementExtractId())) {
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
        BigDecimal recall = BigDecimal.valueOf(hit)
                .divide(BigDecimal.valueOf(Math.max(1, riskTokens.size())), 4, RoundingMode.HALF_UP);
        if (hit < 2) {
            return recall;
        }
        BigDecimal compactMatch = BigDecimal.valueOf(hit)
                .divide(BigDecimal.valueOf(Math.max(1, Math.min(riskTokens.size(), caseTokens.size()))), 4, RoundingMode.HALF_UP);
        return recall.max(compactMatch);
    }

    private Set<String> tokenize(String value) {
        String normalized = splitCamelCase(value).toLowerCase(Locale.ROOT)
                .replaceAll("[\\[\\]{}\"':,./\\\\()_\\-]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : normalized.split(" ")) {
            if (token.length() >= 2 && !NOISE_TOKENS.contains(token)) {
                tokens.add(token);
            }
        }
        addRawTokens(value, tokens);
        String compact = normalized.replace(" ", "");
        for (int i = 0; i < compact.length() - 1; i++) {
            char c1 = compact.charAt(i);
            char c2 = compact.charAt(i + 1);
            if (isChinese(c1) || isChinese(c2)) {
                tokens.add(compact.substring(i, i + 2));
            }
        }
        return tokens;
    }

    private void addRawTokens(String value, Set<String> tokens) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\[\\]{}\"':,./\\\\()_\\-]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        for (String token : normalized.split(" ")) {
            if (token.length() >= 2 && !NOISE_TOKENS.contains(token)) {
                tokens.add(token);
            }
        }
    }

    private String splitCamelCase(String value) {
        return value == null ? "" : value.replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ");
    }

    private boolean isChinese(char c) {
        return Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN;
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
