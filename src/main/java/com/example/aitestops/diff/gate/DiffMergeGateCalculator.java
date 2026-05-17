package com.example.aitestops.diff.gate;

import com.example.aitestops.diff.entity.AiTestopsDiffRiskItem;
import com.example.aitestops.diff.enums.DiffCoverageStatusEnum;
import com.example.aitestops.diff.enums.DiffMergeGateStatusEnum;
import com.example.aitestops.diff.enums.DiffRiskLevelEnum;
import com.example.aitestops.diff.enums.DiffRiskProcessStatusEnum;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Diff 合并准入门禁计算器。
 * <p>
 * 根据所有风险项的状态综合计算合并准入结论：
 * <ul>
 *   <li>PASS - 所有风险均已覆盖或无风险项</li>
 *   <li>WARNING - 存在中低风险未覆盖或部分覆盖</li>
 *   <li>BLOCK - 存在高风险未覆盖、验证失败或阻塞风险</li>
 *   <li>MANUAL_REVIEW - 存在覆盖关系需人工判断</li>
 * </ul>
 * </p>
 */
@Component
public class DiffMergeGateCalculator {

    /**
     * 计算合并准入状态。
     *
     * @param risks 风险项列表
     * @return 合并准入状态枚举
     */
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
