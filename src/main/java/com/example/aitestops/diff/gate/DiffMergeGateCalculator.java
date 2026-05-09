package com.example.aitestops.diff.gate;

import com.example.aitestops.diff.entity.AiTestopsDiffRiskItem;
import com.example.aitestops.diff.enums.DiffCoverageStatusEnum;
import com.example.aitestops.diff.enums.DiffMergeGateStatusEnum;
import com.example.aitestops.diff.enums.DiffRiskLevelEnum;
import com.example.aitestops.diff.enums.DiffRiskProcessStatusEnum;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DiffMergeGateCalculator {

    public DiffMergeGateStatusEnum calculate(List<AiTestopsDiffRiskItem> risks) {
        if (risks == null || risks.isEmpty()) {
            return DiffMergeGateStatusEnum.PASS;
        }
        boolean hasManualReview = false;
        boolean hasWarning = false;
        for (AiTestopsDiffRiskItem risk : risks) {
            if (DiffRiskProcessStatusEnum.BLOCKED.name().equals(risk.getProcessStatus())
                    || DiffRiskProcessStatusEnum.FAILED.name().equals(risk.getProcessStatus())) {
                return DiffMergeGateStatusEnum.BLOCK;
            }
            if (DiffRiskLevelEnum.HIGH.name().equals(risk.getRiskLevel())
                    && DiffCoverageStatusEnum.NOT_COVERED.name().equals(risk.getCoverageStatus())) {
                return DiffMergeGateStatusEnum.BLOCK;
            }
            if (DiffCoverageStatusEnum.NEED_CONFIRM.name().equals(risk.getCoverageStatus())) {
                hasManualReview = true;
            }
            if (DiffRiskLevelEnum.MEDIUM.name().equals(risk.getRiskLevel())
                    && DiffCoverageStatusEnum.NOT_COVERED.name().equals(risk.getCoverageStatus())) {
                hasWarning = true;
            }
            if (DiffCoverageStatusEnum.PARTIAL_COVERED.name().equals(risk.getCoverageStatus())) {
                hasWarning = true;
            }
        }
        if (hasManualReview) {
            return DiffMergeGateStatusEnum.MANUAL_REVIEW;
        }
        if (hasWarning) {
            return DiffMergeGateStatusEnum.WARNING;
        }
        return DiffMergeGateStatusEnum.PASS;
    }
}
