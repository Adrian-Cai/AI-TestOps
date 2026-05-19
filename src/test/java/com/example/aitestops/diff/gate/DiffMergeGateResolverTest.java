package com.example.aitestops.diff.gate;

import com.example.aitestops.diff.entity.AiTestopsDiffRiskItem;
import com.example.aitestops.diff.enums.DiffCoverageStatusEnum;
import com.example.aitestops.diff.enums.DiffMergeGateStatusEnum;
import com.example.aitestops.diff.enums.DiffRiskLevelEnum;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiffMergeGateResolverTest {

    @Test
    void resolveRiskGateImpactShouldPassForNullRisk() {
        assertThat(DiffMergeGateResolver.resolveRiskGateImpact(null))
                .isEqualTo(DiffMergeGateStatusEnum.PASS.name());
    }

    @Test
    void resolveRiskGateImpactShouldBlockHighRiskWithoutCoverage() {
        assertThat(DiffMergeGateResolver.resolveRiskGateImpact(risk(DiffRiskLevelEnum.HIGH, DiffCoverageStatusEnum.NOT_COVERED)))
                .isEqualTo(DiffMergeGateStatusEnum.BLOCK.name());
    }

    @Test
    void resolveRiskGateImpactShouldRequestManualReviewWhenCoverageNeedsConfirmation() {
        assertThat(DiffMergeGateResolver.resolveRiskGateImpact(risk(DiffRiskLevelEnum.LOW, DiffCoverageStatusEnum.NEED_CONFIRM)))
                .isEqualTo(DiffMergeGateStatusEnum.MANUAL_REVIEW.name());
    }

    @Test
    void resolveRiskGateImpactShouldWarnForPartialCoverage() {
        assertThat(DiffMergeGateResolver.resolveRiskGateImpact(risk(DiffRiskLevelEnum.MEDIUM, DiffCoverageStatusEnum.PARTIAL_COVERED)))
                .isEqualTo(DiffMergeGateStatusEnum.WARNING.name());
    }

    @Test
    void resolveRiskGateImpactShouldPassForCoveredRisk() {
        assertThat(DiffMergeGateResolver.resolveRiskGateImpact(risk(DiffRiskLevelEnum.HIGH, DiffCoverageStatusEnum.COVERED)))
                .isEqualTo(DiffMergeGateStatusEnum.PASS.name());
    }

    private AiTestopsDiffRiskItem risk(DiffRiskLevelEnum level, DiffCoverageStatusEnum coverage) {
        AiTestopsDiffRiskItem risk = new AiTestopsDiffRiskItem();
        risk.setRiskLevel(level.name());
        risk.setCoverageStatus(coverage.name());
        return risk;
    }
}
