package com.example.aitestops.diff.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.aitestops.common.util.IdGenerator;
import com.example.aitestops.common.util.JsonUtil;
import com.example.aitestops.diff.entity.AiTestopsDiffAnalysisTask;
import com.example.aitestops.diff.entity.AiTestopsDiffChangedFile;
import com.example.aitestops.diff.entity.AiTestopsDiffMergeGateReport;
import com.example.aitestops.diff.entity.AiTestopsDiffRiskItem;
import com.example.aitestops.diff.enums.DiffCoverageStatusEnum;
import com.example.aitestops.diff.enums.DiffMergeGateStatusEnum;
import com.example.aitestops.diff.enums.DiffRiskLevelEnum;
import com.example.aitestops.diff.enums.DiffRiskProcessStatusEnum;
import com.example.aitestops.diff.gate.DiffMergeGateCalculator;
import com.example.aitestops.diff.mapper.AiTestopsDiffAnalysisTaskMapper;
import com.example.aitestops.diff.service.AiTestopsDiffChangedFileService;
import com.example.aitestops.diff.service.AiTestopsDiffMergeGateReportService;
import com.example.aitestops.diff.service.AiTestopsDiffRiskItemService;
import com.example.aitestops.diff.service.DiffReportRefreshService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DiffReportRefreshServiceImpl implements DiffReportRefreshService {

    private final AiTestopsDiffRiskItemService riskItemService;
    private final AiTestopsDiffChangedFileService changedFileService;
    private final AiTestopsDiffMergeGateReportService reportService;
    private final AiTestopsDiffAnalysisTaskMapper taskMapper;
    private final DiffMergeGateCalculator gateCalculator;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void refresh(Long taskId) {
        List<AiTestopsDiffRiskItem> risks = riskItemService.list(new LambdaQueryWrapper<AiTestopsDiffRiskItem>()
                .eq(AiTestopsDiffRiskItem::getTaskId, taskId));
        List<AiTestopsDiffChangedFile> files = changedFileService.list(new LambdaQueryWrapper<AiTestopsDiffChangedFile>()
                .eq(AiTestopsDiffChangedFile::getTaskId, taskId));
        DiffMergeGateStatusEnum gate = gateCalculator.calculate(risks);
        AiTestopsDiffMergeGateReport report = reportService.getOne(new LambdaQueryWrapper<AiTestopsDiffMergeGateReport>()
                .eq(AiTestopsDiffMergeGateReport::getTaskId, taskId)
                .last("limit 1"), false);
        if (report == null) {
            report = new AiTestopsDiffMergeGateReport();
            report.setTaskId(taskId);
            report.setReportCode(IdGenerator.diffReportCode());
        }
        report.setGateStatus(gate.name());
        report.setGateReason(buildGateReason(gate));
        report.setChangedFileCount(files.size());
        report.setChangedMethodCount(0);
        report.setHighRiskCount(countRiskLevel(risks, DiffRiskLevelEnum.HIGH.name()));
        report.setMediumRiskCount(countRiskLevel(risks, DiffRiskLevelEnum.MEDIUM.name()));
        report.setLowRiskCount(countRiskLevel(risks, DiffRiskLevelEnum.LOW.name()));
        report.setCoveredRiskCount(countCoverage(risks, DiffCoverageStatusEnum.COVERED.name()));
        report.setPartialCoveredRiskCount(countCoverage(risks, DiffCoverageStatusEnum.PARTIAL_COVERED.name()));
        report.setNotCoveredRiskCount(countCoverage(risks, DiffCoverageStatusEnum.NOT_COVERED.name()));
        report.setNeedConfirmRiskCount(countCoverage(risks, DiffCoverageStatusEnum.NEED_CONFIRM.name()));
        report.setBlockedRiskCount((int) risks.stream()
                .filter(r -> DiffRiskProcessStatusEnum.BLOCKED.name().equals(r.getProcessStatus()))
                .count());
        report.setSuggestedCaseCount((int) risks.stream()
                .filter(r -> DiffCoverageStatusEnum.NOT_COVERED.name().equals(r.getCoverageStatus())
                        || DiffCoverageStatusEnum.PARTIAL_COVERED.name().equals(r.getCoverageStatus()))
                .count());
        report.setSuggestedRegressionModules(JsonUtil.toJson(objectMapper, risks.stream()
                .map(AiTestopsDiffRiskItem::getAffectedModule)
                .filter(Objects::nonNull)
                .distinct()
                .toList()));
        report.setReportSummary("Diff 分析识别风险 %d 项，准入结论 %s".formatted(risks.size(), gate.name()));
        report.setReportDetail(JsonUtil.toJson(objectMapper, Map.of("riskCount", risks.size())));
        report.setCreatedAt(report.getCreatedAt() == null ? LocalDateTime.now() : report.getCreatedAt());
        if (report.getId() == null) {
            reportService.save(report);
        } else {
            reportService.updateById(report);
        }
        taskMapper.update(null, new LambdaUpdateWrapper<AiTestopsDiffAnalysisTask>()
                .eq(AiTestopsDiffAnalysisTask::getId, taskId)
                .set(AiTestopsDiffAnalysisTask::getChangedFileCount, files.size())
                .set(AiTestopsDiffAnalysisTask::getHighRiskCount, report.getHighRiskCount())
                .set(AiTestopsDiffAnalysisTask::getMediumRiskCount, report.getMediumRiskCount())
                .set(AiTestopsDiffAnalysisTask::getLowRiskCount, report.getLowRiskCount())
                .set(AiTestopsDiffAnalysisTask::getNotCoveredRiskCount, report.getNotCoveredRiskCount())
                .set(AiTestopsDiffAnalysisTask::getMergeGateStatus, gate.name())
                .set(AiTestopsDiffAnalysisTask::getUpdatedAt, LocalDateTime.now()));
    }

    private int countRiskLevel(List<AiTestopsDiffRiskItem> risks, String level) {
        return (int) risks.stream().filter(item -> level.equals(item.getRiskLevel())).count();
    }

    private int countCoverage(List<AiTestopsDiffRiskItem> risks, String status) {
        return (int) risks.stream().filter(item -> status.equals(item.getCoverageStatus())).count();
    }

    private String buildGateReason(DiffMergeGateStatusEnum gate) {
        return switch (gate) {
            case PASS -> "所有风险均已覆盖或当前无风险项";
            case WARNING -> "存在中低风险未覆盖或部分覆盖风险，建议关注后合并";
            case BLOCK -> "存在高风险未覆盖、验证失败或阻塞风险，不建议合并";
            case MANUAL_REVIEW -> "存在覆盖关系无法自动判断的风险，需要人工评审";
        };
    }
}
