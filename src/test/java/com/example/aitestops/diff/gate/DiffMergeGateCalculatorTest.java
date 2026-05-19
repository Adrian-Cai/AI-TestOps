package com.example.aitestops.diff.gate;

import com.example.aitestops.diff.entity.AiTestopsDiffRiskItem;
import com.example.aitestops.diff.enums.DiffCoverageStatusEnum;
import com.example.aitestops.diff.enums.DiffMergeGateStatusEnum;
import com.example.aitestops.diff.enums.DiffRiskLevelEnum;
import com.example.aitestops.diff.enums.DiffRiskProcessStatusEnum;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiffMergeGateCalculatorTest {

    private final DiffMergeGateCalculator calculator = new DiffMergeGateCalculator();

    @Test
    void calculateShouldPassWhenThereAreNoRisks() {
        assertThat(calculator.calculate(null)).isEqualTo(DiffMergeGateStatusEnum.PASS);
        assertThat(calculator.calculate(List.of())).isEqualTo(DiffMergeGateStatusEnum.PASS);
    }

    @Test
    void calculateShouldBlockHighRiskThatHasNoCoverage() {
        assertThat(calculator.calculate(List.of(risk(DiffRiskLevelEnum.HIGH, DiffCoverageStatusEnum.NOT_COVERED, DiffRiskProcessStatusEnum.PENDING))))
                .isEqualTo(DiffMergeGateStatusEnum.BLOCK);
    }

    @Test
    void calculateShouldBlockFailedOrBlockedProcessStatus() {
        assertThat(calculator.calculate(List.of(risk(DiffRiskLevelEnum.LOW, DiffCoverageStatusEnum.COVERED, DiffRiskProcessStatusEnum.FAILED))))
                .isEqualTo(DiffMergeGateStatusEnum.BLOCK);
        assertThat(calculator.calculate(List.of(risk(DiffRiskLevelEnum.LOW, DiffCoverageStatusEnum.COVERED, DiffRiskProcessStatusEnum.BLOCKED))))
                .isEqualTo(DiffMergeGateStatusEnum.BLOCK);
    }

    @Test
    void calculateShouldRequestManualReviewBeforeWarning() {
        List<AiTestopsDiffRiskItem> risks = List.of(
                risk(DiffRiskLevelEnum.LOW, DiffCoverageStatusEnum.PARTIAL_COVERED, DiffRiskProcessStatusEnum.PENDING),
                risk(DiffRiskLevelEnum.LOW, DiffCoverageStatusEnum.NEED_CONFIRM, DiffRiskProcessStatusEnum.PENDING)
        );

        assertThat(calculator.calculate(risks)).isEqualTo(DiffMergeGateStatusEnum.MANUAL_REVIEW);
    }

    @Test
    void calculateShouldWarnForMediumRiskWithoutCoverageOrPartialCoverage() {
        assertThat(calculator.calculate(List.of(risk(DiffRiskLevelEnum.MEDIUM, DiffCoverageStatusEnum.NOT_COVERED, DiffRiskProcessStatusEnum.PENDING))))
                .isEqualTo(DiffMergeGateStatusEnum.WARNING);
        assertThat(calculator.calculate(List.of(risk(DiffRiskLevelEnum.LOW, DiffCoverageStatusEnum.PARTIAL_COVERED, DiffRiskProcessStatusEnum.PENDING))))
                .isEqualTo(DiffMergeGateStatusEnum.WARNING);
    }

    @Test
    void calculateShouldPassWhenRisksAreCovered() {
        assertThat(calculator.calculate(List.of(risk(DiffRiskLevelEnum.HIGH, DiffCoverageStatusEnum.COVERED, DiffRiskProcessStatusEnum.PENDING))))
                .isEqualTo(DiffMergeGateStatusEnum.PASS);
    }

    private AiTestopsDiffRiskItem risk(DiffRiskLevelEnum level, DiffCoverageStatusEnum coverage, DiffRiskProcessStatusEnum processStatus) {
        AiTestopsDiffRiskItem risk = new AiTestopsDiffRiskItem();
        risk.setRiskLevel(level.name());
        risk.setCoverageStatus(coverage.name());
        risk.setProcessStatus(processStatus.name());
        return risk;
    }
}
