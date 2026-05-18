package com.example.aitestops.diff.coverage;

import com.example.aitestops.diff.entity.AiTestopsDiffRiskCaseRel;
import com.example.aitestops.diff.entity.AiTestopsDiffRiskItem;
import com.example.aitestops.diff.enums.DiffCoverageStatusEnum;
import com.example.aitestops.diff.enums.DiffRiskProcessStatusEnum;
import com.example.aitestops.diff.gate.DiffMergeGateResolver;
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

/**
 * Diff 补充用例关联服务。
 * <p>
 * 当补充用例草稿被审批通过后，负责将正式测试用例关联到对应的风险项，
 * 更新风险的覆盖状态、处理状态和准入影响，并触发报告刷新。
 * </p>
 */
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

    /**
     * 关联已审批通过的补充用例到风险项。
     * <p>
     * 从草稿元数据中解析目标风险 ID，确保风险-用例关联关系存在，
     * 更新风险的覆盖状态、处理状态和准入影响，并触发报告刷新。
     * 操作具有幂等性，并发审批场景下不会产生重复关联。
     * </p>
     *
     * @param draft    补充用例草稿
     * @param testCase 正式测试用例
     * @param operator 操作人
     */
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
        risk.setMergeGateImpact(DiffMergeGateResolver.resolveRiskGateImpact(risk));
        risk.setUpdatedAt(LocalDateTime.now());
        if (!riskItemService.updateById(risk)) {
            return;
        }
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

}
