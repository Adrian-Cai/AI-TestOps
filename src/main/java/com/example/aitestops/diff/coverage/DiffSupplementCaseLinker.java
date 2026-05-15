package com.example.aitestops.diff.coverage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aitestops.diff.entity.AiTestopsDiffRiskCaseRel;
import com.example.aitestops.diff.entity.AiTestopsDiffRiskItem;
import com.example.aitestops.diff.enums.DiffCoverageStatusEnum;
import com.example.aitestops.diff.enums.DiffMergeGateStatusEnum;
import com.example.aitestops.diff.enums.DiffRiskLevelEnum;
import com.example.aitestops.diff.enums.DiffRiskProcessStatusEnum;
import com.example.aitestops.diff.service.AiTestopsDiffRiskCaseRelService;
import com.example.aitestops.diff.service.AiTestopsDiffRiskItemService;
import com.example.aitestops.diff.service.DiffReportRefreshService;
import com.example.aitestops.testcase.entity.AiTestopsTestCase;
import com.example.aitestops.testcase.entity.AiTestopsTestCaseDraft;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DiffSupplementCaseLinker {

    private static final String CASE_TYPE = "DIFF_SUPPLEMENT";
    private static final String GENERATION_PREFIX = "DIFF_SUPPLEMENT_";
    private static final Set<String> TERMINAL_STATUSES = Set.of(
            DiffRiskProcessStatusEnum.PASSED.name(),
            DiffRiskProcessStatusEnum.FAILED.name(),
            DiffRiskProcessStatusEnum.BLOCKED.name(),
            DiffRiskProcessStatusEnum.CLOSED.name(),
            DiffRiskProcessStatusEnum.IGNORED.name()
    );

    private final AiTestopsDiffRiskItemService riskItemService;
    private final AiTestopsDiffRiskCaseRelService riskCaseRelService;
    private final DiffReportRefreshService reportRefreshService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void linkApprovedSupplementCase(AiTestopsTestCaseDraft draft, AiTestopsTestCase testCase, String operator) {
        Long riskId = resolveRiskId(draft);
        if (riskId == null || testCase == null || testCase.getId() == null) {
            return;
        }
        AiTestopsDiffRiskItem risk = riskItemService.getById(riskId);
        if (risk == null) {
            return;
        }
        ensureRelation(risk, testCase, operator);

        risk.setCoverageStatus(DiffCoverageStatusEnum.COVERED.name());
        risk.setCoverageReason("Diff 补充用例已确认并关联到该风险");
        if (!TERMINAL_STATUSES.contains(risk.getProcessStatus())) {
            risk.setProcessStatus(DiffRiskProcessStatusEnum.WAIT_TEST.name());
        }
        risk.setMergeGateImpact(resolveRiskGateImpact(risk));
        risk.setUpdatedAt(LocalDateTime.now());
        riskItemService.updateById(risk);
        reportRefreshService.refresh(risk.getTaskId());
    }

    private void ensureRelation(AiTestopsDiffRiskItem risk, AiTestopsTestCase testCase, String operator) {
        AiTestopsDiffRiskCaseRel rel = new AiTestopsDiffRiskCaseRel();
        rel.setRiskId(risk.getId());
        rel.setCaseId(testCase.getId());
        rel.setDocumentId(testCase.getDocumentId());
        rel.setRequirementExtractId(testCase.getRequirementExtractId());
        rel.setCaseSourceType("FORMAL");
        rel.setRelationType("GENERATED_SUPPLEMENT_CASE");
        rel.setCoverageJudgement(DiffCoverageStatusEnum.COVERED.name());
        rel.setJudgementReason("由 Diff 补充用例草稿确认生成并自动关联");
        rel.setSimilarityScore(BigDecimal.ONE);
        rel.setGeneratedFromAi(0);
        rel.setCreatedBy(operator);
        rel.setCreatedAt(LocalDateTime.now());
        try {
            riskCaseRelService.save(rel);
        } catch (org.springframework.dao.DuplicateKeyException ignored) {
            // idempotent under concurrent approvals
        }
    }

    private Long resolveRiskId(AiTestopsTestCaseDraft draft) {
        if (draft == null || !CASE_TYPE.equals(draft.getCaseType())) {
            return null;
        }
        Long rawRiskId = resolveRiskIdFromRawCase(draft.getRawCaseJson());
        if (rawRiskId != null) {
            return rawRiskId;
        }
        return resolveRiskIdFromGenerationId(draft.getGenerationId());
    }

    private Long resolveRiskIdFromRawCase(String rawCaseJson) {
        if (!StringUtils.hasText(rawCaseJson)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(rawCaseJson);
            if (!CASE_TYPE.equals(root.path("source_type").asText())) {
                return null;
            }
            JsonNode sourceId = root.get("source_id");
            if (sourceId == null || sourceId.isNull()) {
                return null;
            }
            if (sourceId.isNumber()) {
                return sourceId.asLong();
            }
            String text = sourceId.asText();
            return StringUtils.hasText(text) ? Long.valueOf(text) : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private Long resolveRiskIdFromGenerationId(String generationId) {
        if (!StringUtils.hasText(generationId) || !generationId.startsWith(GENERATION_PREFIX)) {
            return null;
        }
        String remainder = generationId.substring(GENERATION_PREFIX.length());
        int nextSeparator = remainder.indexOf('_');
        String riskId = nextSeparator >= 0 ? remainder.substring(0, nextSeparator) : remainder;
        try {
            return StringUtils.hasText(riskId) ? Long.valueOf(riskId) : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String resolveRiskGateImpact(AiTestopsDiffRiskItem risk) {
        if (DiffRiskLevelEnum.HIGH.name().equals(risk.getRiskLevel())
                && DiffCoverageStatusEnum.NOT_COVERED.name().equals(risk.getCoverageStatus())) {
            return DiffMergeGateStatusEnum.BLOCK.name();
        }
        if (DiffCoverageStatusEnum.NEED_CONFIRM.name().equals(risk.getCoverageStatus())) {
            return DiffMergeGateStatusEnum.MANUAL_REVIEW.name();
        }
        if (DiffCoverageStatusEnum.PARTIAL_COVERED.name().equals(risk.getCoverageStatus())) {
            return DiffMergeGateStatusEnum.WARNING.name();
        }
        return DiffMergeGateStatusEnum.PASS.name();
    }
}
