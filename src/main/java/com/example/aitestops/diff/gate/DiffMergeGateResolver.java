package com.example.aitestops.diff.gate;

import com.example.aitestops.diff.entity.AiTestopsDiffRiskItem;
import com.example.aitestops.diff.enums.DiffCoverageStatusEnum;
import com.example.aitestops.diff.enums.DiffMergeGateStatusEnum;
import com.example.aitestops.diff.enums.DiffRiskLevelEnum;

/**
 * Diff 合并准入影响解析工具类。
 * <p>
 * 根据风险项的风险等级和覆盖状态，计算单个风险对合并准入门禁的影响。
 * </p>
 */
public final class DiffMergeGateResolver {

    private DiffMergeGateResolver() {
    }

    /**
     * 解析风险项对合并准入门禁的影响。
     * <p>
     * 规则：
     * <ul>
     *   <li>高风险且未覆盖 → BLOCK</li>
     *   <li>需确认覆盖 → MANUAL_REVIEW</li>
     *   <li>部分覆盖 → WARNING</li>
     *   <li>其他 → PASS</li>
     * </ul>
     * </p>
     *
     * @param risk 风险项实体
     * @return 合并准入状态枚举名称
     */
    public static String resolveRiskGateImpact(AiTestopsDiffRiskItem risk) {
        if (risk == null) {
            return DiffMergeGateStatusEnum.PASS.name();
        }
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
