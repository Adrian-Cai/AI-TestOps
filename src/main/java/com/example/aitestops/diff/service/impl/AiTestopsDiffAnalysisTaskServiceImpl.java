package com.example.aitestops.diff.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.aitestops.common.exception.BusinessException;
import com.example.aitestops.common.exception.ErrorCode;
import com.example.aitestops.common.util.IdGenerator;
import com.example.aitestops.common.util.JsonUtil;
import com.example.aitestops.diff.coverage.DiffCoverageMatcher;
import com.example.aitestops.diff.dto.DiffAnalysisOptions;
import com.example.aitestops.diff.dto.DiffAnalysisTaskCreateRequest;
import com.example.aitestops.diff.dto.DiffRiskActionRequest;
import com.example.aitestops.diff.dto.DiffRiskVerifyRequest;
import com.example.aitestops.diff.entity.AiTestopsDiffAnalysisTask;
import com.example.aitestops.diff.entity.AiTestopsDiffChangedFile;
import com.example.aitestops.diff.entity.AiTestopsDiffMergeGateReport;
import com.example.aitestops.diff.entity.AiTestopsDiffRiskActionRecord;
import com.example.aitestops.diff.entity.AiTestopsDiffRiskCaseRel;
import com.example.aitestops.diff.entity.AiTestopsDiffRiskItem;
import com.example.aitestops.diff.enums.DiffAnalysisTaskStatusEnum;
import com.example.aitestops.diff.enums.DiffCoverageStatusEnum;
import com.example.aitestops.diff.enums.DiffFileRoleEnum;
import com.example.aitestops.diff.enums.DiffMergeGateStatusEnum;
import com.example.aitestops.diff.enums.DiffRiskLevelEnum;
import com.example.aitestops.diff.enums.DiffRiskProcessStatusEnum;
import com.example.aitestops.diff.gate.DiffMergeGateCalculator;
import com.example.aitestops.diff.git.GitChangedFile;
import com.example.aitestops.diff.git.GitDiffClient;
import com.example.aitestops.diff.git.GitDiffResult;
import com.example.aitestops.diff.mapper.AiTestopsDiffAnalysisTaskMapper;
import com.example.aitestops.diff.risk.DiffFileClassifier;
import com.example.aitestops.diff.risk.DiffRuleRiskService;
import com.example.aitestops.diff.service.AiTestopsDiffAnalysisTaskService;
import com.example.aitestops.diff.service.AiTestopsDiffChangedFileService;
import com.example.aitestops.diff.service.AiTestopsDiffMergeGateReportService;
import com.example.aitestops.diff.service.AiTestopsDiffRiskActionRecordService;
import com.example.aitestops.diff.service.AiTestopsDiffRiskCaseRelService;
import com.example.aitestops.diff.service.AiTestopsDiffRiskItemService;
import com.example.aitestops.diff.vo.DiffAnalysisReportVO;
import com.example.aitestops.diff.vo.DiffAnalysisTaskVO;
import com.example.aitestops.diff.vo.DiffChangedFileVO;
import com.example.aitestops.diff.vo.DiffMergeGateReportVO;
import com.example.aitestops.diff.vo.DiffRiskCaseRelVO;
import com.example.aitestops.diff.vo.DiffRiskItemVO;
import com.example.aitestops.diff.vo.DiffSupplementCaseVO;
import com.example.aitestops.testcase.entity.AiTestopsTestCase;
import com.example.aitestops.testcase.entity.AiTestopsTestCaseDraft;
import com.example.aitestops.testcase.service.AiTestopsTestCaseDraftService;
import com.example.aitestops.testcase.service.AiTestopsTestCaseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiTestopsDiffAnalysisTaskServiceImpl
        extends ServiceImpl<AiTestopsDiffAnalysisTaskMapper, AiTestopsDiffAnalysisTask>
        implements AiTestopsDiffAnalysisTaskService {

    private final GitDiffClient gitDiffClient;
    private final DiffFileClassifier fileClassifier;
    private final DiffRuleRiskService ruleRiskService;
    private final DiffCoverageMatcher coverageMatcher;
    private final DiffMergeGateCalculator gateCalculator;
    private final AiTestopsDiffChangedFileService changedFileService;
    private final AiTestopsDiffRiskItemService riskItemService;
    private final AiTestopsDiffRiskCaseRelService riskCaseRelService;
    private final AiTestopsDiffRiskActionRecordService actionRecordService;
    private final AiTestopsDiffMergeGateReportService reportService;
    private final AiTestopsTestCaseService testCaseService;
    private final AiTestopsTestCaseDraftService draftService;
    private final ObjectMapper objectMapper;

    @Override
    public DiffAnalysisTaskVO createAndAnalyze(DiffAnalysisTaskCreateRequest request) {
        validateCreateRequest(request);
        DiffAnalysisOptions options = normalizeOptions(request);
        assertNoRunningDuplicate(request);
        AiTestopsDiffAnalysisTask task = createTaskAndSave(request, options);
        try {
            runAnalysisAndSave(task, options);
            task = getById(task.getId());
        } catch (Exception ex) {
            log.warn("Diff 分析任务失败: taskId={}", task.getId(), ex);
            update(new LambdaUpdateWrapper<AiTestopsDiffAnalysisTask>()
                    .eq(AiTestopsDiffAnalysisTask::getId, task.getId())
                    .set(AiTestopsDiffAnalysisTask::getStatus, DiffAnalysisTaskStatusEnum.FAILED.name())
                    .set(AiTestopsDiffAnalysisTask::getFailReason, truncate(ex.getMessage(), 1000))
                    .set(AiTestopsDiffAnalysisTask::getUpdatedAt, LocalDateTime.now()));
            task = getById(task.getId());
        }
        return toTaskVO(task);
    }

    @Override
    public List<DiffAnalysisTaskVO> listTasks(String documentId, String requirementExtractId, String status,
                                             String sourceBranch, String targetBranch) {
        LambdaQueryWrapper<AiTestopsDiffAnalysisTask> wrapper = new LambdaQueryWrapper<AiTestopsDiffAnalysisTask>()
                .orderByDesc(AiTestopsDiffAnalysisTask::getCreatedAt);
        if (StringUtils.hasText(documentId)) wrapper.eq(AiTestopsDiffAnalysisTask::getDocumentId, documentId);
        if (StringUtils.hasText(requirementExtractId)) wrapper.eq(AiTestopsDiffAnalysisTask::getRequirementExtractId, requirementExtractId);
        if (StringUtils.hasText(status)) wrapper.eq(AiTestopsDiffAnalysisTask::getStatus, status);
        if (StringUtils.hasText(sourceBranch)) wrapper.eq(AiTestopsDiffAnalysisTask::getSourceBranch, sourceBranch);
        if (StringUtils.hasText(targetBranch)) wrapper.eq(AiTestopsDiffAnalysisTask::getTargetBranch, targetBranch);
        return list(wrapper).stream().map(this::toTaskVO).toList();
    }

    @Override
    public DiffAnalysisReportVO getReport(Long taskId) {
        AiTestopsDiffAnalysisTask task = requireTask(taskId);
        List<AiTestopsDiffChangedFile> files = changedFileService.list(new LambdaQueryWrapper<AiTestopsDiffChangedFile>()
                .eq(AiTestopsDiffChangedFile::getTaskId, taskId)
                .orderByAsc(AiTestopsDiffChangedFile::getId));
        List<AiTestopsDiffRiskItem> risks = riskItemService.list(new LambdaQueryWrapper<AiTestopsDiffRiskItem>()
                .eq(AiTestopsDiffRiskItem::getTaskId, taskId)
                .orderByAsc(AiTestopsDiffRiskItem::getId));
        AiTestopsDiffMergeGateReport report = reportService.getOne(new LambdaQueryWrapper<AiTestopsDiffMergeGateReport>()
                .eq(AiTestopsDiffMergeGateReport::getTaskId, taskId)
                .last("limit 1"), false);

        List<Long> riskIds = risks.stream().map(AiTestopsDiffRiskItem::getId).toList();
        Map<Long, List<AiTestopsDiffRiskCaseRel>> relationsByRiskId = riskIds.isEmpty()
                ? Map.of()
                : riskCaseRelService.list(new LambdaQueryWrapper<AiTestopsDiffRiskCaseRel>()
                        .in(AiTestopsDiffRiskCaseRel::getRiskId, riskIds))
                        .stream().collect(Collectors.groupingBy(AiTestopsDiffRiskCaseRel::getRiskId));
        Set<Long> caseDbIds = relationsByRiskId.values().stream()
                .flatMap(List::stream).map(AiTestopsDiffRiskCaseRel::getCaseId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, AiTestopsTestCase> testCaseMap = caseDbIds.isEmpty()
                ? Map.of()
                : testCaseService.listByIds(caseDbIds).stream().collect(Collectors.toMap(AiTestopsTestCase::getId, t -> t));

        DiffAnalysisReportVO vo = new DiffAnalysisReportVO();
        vo.setTask(toTaskVO(task));
        vo.setChangedFiles(files.stream().map(this::toFileVO).toList());
        vo.setRiskList(risks.stream().map(r -> toRiskVO(r, relationsByRiskId.getOrDefault(r.getId(), List.of()), testCaseMap)).toList());
        vo.setReport(toReportVO(report));
        return vo;
    }

    @Override
    @Transactional
    public void applyRiskAction(Long riskId, DiffRiskActionRequest request) {
        AiTestopsDiffRiskItem risk = requireRisk(riskId);
        String before = risk.getProcessStatus();
        String actionType = request == null ? null : request.getActionType();
        String after = switch (String.valueOf(actionType)) {
            case "CONFIRM" -> DiffCoverageStatusEnum.NOT_COVERED.name().equals(risk.getCoverageStatus())
                    ? DiffRiskProcessStatusEnum.WAIT_CASE.name() : DiffRiskProcessStatusEnum.CONFIRMED.name();
            case "IGNORE" -> {
                if (!StringUtils.hasText(request.getIgnoreReason()) && !StringUtils.hasText(request.getActionDesc())) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "忽略风险必须填写原因");
                }
                risk.setIgnoreReason(StringUtils.hasText(request.getIgnoreReason()) ? request.getIgnoreReason() : request.getActionDesc());
                yield DiffRiskProcessStatusEnum.IGNORED.name();
            }
            case "LINK_CASE" -> DiffRiskProcessStatusEnum.WAIT_TEST.name();
            case "MARK_PASS" -> DiffRiskProcessStatusEnum.PASSED.name();
            case "MARK_FAIL" -> DiffRiskProcessStatusEnum.FAILED.name();
            case "MARK_BLOCKED" -> DiffRiskProcessStatusEnum.BLOCKED.name();
            case "CLOSE" -> DiffRiskProcessStatusEnum.CLOSED.name();
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的风险动作: " + actionType);
        };
        risk.setProcessStatus(after);
        risk.setUpdatedAt(LocalDateTime.now());
        riskItemService.updateById(risk);
        saveAction(risk, actionType, before, after, request == null ? null : request.getActionDesc(), request == null ? null : request.getOperator(), null);
        refreshReport(risk.getTaskId());
    }

    @Override
    @Transactional
    public void verifyRisk(Long riskId, DiffRiskVerifyRequest request) {
        AiTestopsDiffRiskItem risk = requireRisk(riskId);
        String result = request == null ? null : request.getVerifyResult();
        DiffRiskActionRequest actionRequest = new DiffRiskActionRequest();
        actionRequest.setOperator(request == null ? null : request.getOperator());
        actionRequest.setActionDesc(request == null ? null : request.getRemark());
        if (DiffRiskProcessStatusEnum.PASSED.name().equals(result)) {
            actionRequest.setActionType("MARK_PASS");
        } else if (DiffRiskProcessStatusEnum.FAILED.name().equals(result)) {
            actionRequest.setActionType("MARK_FAIL");
        } else if (DiffRiskProcessStatusEnum.BLOCKED.name().equals(result)) {
            actionRequest.setActionType("MARK_BLOCKED");
        } else {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "verifyResult 只支持 PASSED/FAILED/BLOCKED");
        }
        applyRiskAction(riskId, actionRequest);
    }

    @Override
    @Transactional
    public DiffSupplementCaseVO generateSupplementCases(Long riskId) {
        AiTestopsDiffRiskItem risk = requireRisk(riskId);
        if (DiffRiskProcessStatusEnum.CLOSED.name().equals(risk.getProcessStatus())
                || DiffRiskProcessStatusEnum.IGNORED.name().equals(risk.getProcessStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已关闭或已忽略风险不允许生成补充用例");
        }
        if (!DiffCoverageStatusEnum.NOT_COVERED.name().equals(risk.getCoverageStatus())
                && !DiffCoverageStatusEnum.PARTIAL_COVERED.name().equals(risk.getCoverageStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只有未覆盖或部分覆盖风险允许生成补充用例");
        }
        LocalDateTime now = LocalDateTime.now();
        AiTestopsDiffAnalysisTask task = requireTask(risk.getTaskId());
        AiTestopsTestCaseDraft draft = new AiTestopsTestCaseDraft();
        draft.setDraftCaseId(IdGenerator.draftCaseId());
        draft.setCaseId("DIFF_" + risk.getRiskCode());
        draft.setGenerationId("DIFF_SUPPLEMENT_" + risk.getId());
        draft.setDocumentId(task.getDocumentId());
        draft.setRequirementExtractId(task.getRequirementExtractId());
        draft.setTitle("补充验证: " + risk.getRiskTitle());
        draft.setPreconditionsJson(JsonUtil.toJson(objectMapper, List.of("已完成代码 Diff 分析任务 " + task.getTaskCode())));
        draft.setStepsJson(JsonUtil.toJson(objectMapper, List.of(
                Map.of("step_no", 1, "action", "根据风险说明准备测试数据: " + nullToEmpty(risk.getRiskReason())),
                Map.of("step_no", 2, "action", "执行受影响模块验证: " + nullToEmpty(risk.getAffectedModule()))
        )));
        draft.setExpectedResultsJson(JsonUtil.toJson(objectMapper, List.of("风险场景被明确验证，未出现回归问题")));
        draft.setPriority(DiffRiskLevelEnum.HIGH.name().equals(risk.getRiskLevel()) ? "P0" : "P1");
        draft.setCaseType("DIFF_SUPPLEMENT");
        draft.setRiskLevel(draft.getPriority());
        draft.setRequirementRefsJson("[]");
        draft.setRiskTagsJson(JsonUtil.toJson(objectMapper, List.of(risk.getRiskCategory(), "DIFF_SUPPLEMENT")));
        draft.setReviewStatus("PENDING");
        draft.setRawCaseJson(JsonUtil.toJson(objectMapper, Map.of(
                "source_type", "DIFF_SUPPLEMENT",
                "source_id", risk.getId(),
                "risk_code", risk.getRiskCode()
        )));
        draft.setCreatedAt(now);
        draft.setUpdatedAt(now);
        draftService.save(draft);

        String before = risk.getProcessStatus();
        risk.setProcessStatus(DiffRiskProcessStatusEnum.WAIT_TEST.name());
        risk.setUpdatedAt(now);
        riskItemService.updateById(risk);
        saveAction(risk, "GENERATE_CASE", before, risk.getProcessStatus(), "生成 Diff 补充用例草稿", null,
                JsonUtil.toJson(objectMapper, Map.of("draftCaseId", draft.getDraftCaseId())));
        refreshReport(risk.getTaskId());

        DiffSupplementCaseVO.GeneratedCase generated = new DiffSupplementCaseVO.GeneratedCase();
        generated.setDraftCaseId(draft.getDraftCaseId());
        generated.setCaseId(draft.getCaseId());
        generated.setCaseTitle(draft.getTitle());
        generated.setPriority(draft.getPriority());
        generated.setPreconditionsJson(draft.getPreconditionsJson());
        generated.setStepsJson(draft.getStepsJson());
        generated.setExpectedResultsJson(draft.getExpectedResultsJson());
        DiffSupplementCaseVO vo = new DiffSupplementCaseVO();
        vo.setRiskId(riskId);
        vo.setGeneratedCases(List.of(generated));
        return vo;
    }

    private void runAnalysisAndSave(AiTestopsDiffAnalysisTask task, DiffAnalysisOptions options) {
        updateStatus(task.getId(), DiffAnalysisTaskStatusEnum.RUNNING.name(), null);
        GitDiffResult diff = gitDiffClient.diff(task.getRepoUrl(), task.getSourceBranch(), task.getTargetBranch());
        task.setRepoName(diff.repoName());
        task.setBaseCommit(diff.baseCommit());
        task.setHeadCommit(diff.headCommit());
        persistAnalysisResults(task, diff, options);
    }

    @Transactional
    public void persistAnalysisResults(AiTestopsDiffAnalysisTask task, GitDiffResult diff, DiffAnalysisOptions options) {
        List<AiTestopsDiffChangedFile> files = saveChangedFiles(task, diff, options);
        List<AiTestopsDiffRiskItem> risks = ruleRiskService.buildRuleRisks(task, files);
        if (!risks.isEmpty()) {
            riskItemService.saveBatch(risks);
            for (AiTestopsDiffRiskItem risk : risks) {
                DiffCoverageMatcher.CoverageMatchResult match = Boolean.TRUE.equals(options.getEnableCoverageCheck())
                        ? coverageMatcher.match(risk)
                        : new DiffCoverageMatcher.CoverageMatchResult(DiffCoverageStatusEnum.NEED_CONFIRM.name(), "未启用用例覆盖匹配", List.of());
                risk.setCoverageStatus(match.coverageStatus());
                risk.setCoverageReason(match.coverageReason());
                risk.setMergeGateImpact(resolveRiskGateImpact(risk));
                risk.setUpdatedAt(LocalDateTime.now());
                riskItemService.updateById(risk);
                if (!match.relations().isEmpty()) {
                    riskCaseRelService.saveBatch(match.relations());
                }
            }
        }
        refreshReport(task.getId());
        updateStatus(task.getId(), DiffAnalysisTaskStatusEnum.SUCCESS.name(), null);
    }

    private List<AiTestopsDiffChangedFile> saveChangedFiles(AiTestopsDiffAnalysisTask task, GitDiffResult diff, DiffAnalysisOptions options) {
        List<AiTestopsDiffChangedFile> files = new ArrayList<>();
        for (GitChangedFile changed : diff.changedFiles()) {
            String path = changed.newFilePath();
            boolean testFile = fileClassifier.isTestFile(path);
            if (testFile && !Boolean.TRUE.equals(options.getIncludeTestFiles())) {
                continue;
            }
            AiTestopsDiffChangedFile file = new AiTestopsDiffChangedFile();
            file.setTaskId(task.getId());
            file.setOldFilePath(changed.oldFilePath());
            file.setNewFilePath(path);
            file.setChangeType(changed.changeType());
            file.setLanguage(changed.language());
            file.setFileRole(fileClassifier.classify(path).name());
            file.setAdditions(changed.additions());
            file.setDeletions(changed.deletions());
            file.setChanges(changed.additions() + changed.deletions());
            file.setPatch(changed.patch());
            file.setPatchSummary(buildPatchSummary(file));
            file.setIsTestFile(testFile ? 1 : 0);
            file.setIsCoreFile(isCoreRole(file.getFileRole()) ? 1 : 0);
            DiffRuleRiskService.RuleRiskDecision decision = ruleRiskService.decide(file);
            file.setInitialRiskLevel(decision == null ? null : decision.riskLevel());
            file.setInitialRiskReason(decision == null ? null : decision.reason());
            file.setCreatedAt(LocalDateTime.now());
            files.add(file);
        }
        if (!files.isEmpty()) {
            changedFileService.saveBatch(files);
        }
        return files;
    }

    private void refreshReport(Long taskId) {
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
        report.setGateReason(buildGateReason(gate, risks));
        report.setChangedFileCount(files.size());
        report.setChangedMethodCount(null);
        report.setHighRiskCount(countRiskLevel(risks, DiffRiskLevelEnum.HIGH.name()));
        report.setMediumRiskCount(countRiskLevel(risks, DiffRiskLevelEnum.MEDIUM.name()));
        report.setLowRiskCount(countRiskLevel(risks, DiffRiskLevelEnum.LOW.name()));
        report.setCoveredRiskCount(countCoverage(risks, DiffCoverageStatusEnum.COVERED.name()));
        report.setPartialCoveredRiskCount(countCoverage(risks, DiffCoverageStatusEnum.PARTIAL_COVERED.name()));
        report.setNotCoveredRiskCount(countCoverage(risks, DiffCoverageStatusEnum.NOT_COVERED.name()));
        report.setNeedConfirmRiskCount(countCoverage(risks, DiffCoverageStatusEnum.NEED_CONFIRM.name()));
        report.setBlockedRiskCount((int) risks.stream().filter(r -> DiffRiskProcessStatusEnum.BLOCKED.name().equals(r.getProcessStatus())).count());
        report.setSuggestedCaseCount((int) risks.stream().filter(r -> DiffCoverageStatusEnum.NOT_COVERED.name().equals(r.getCoverageStatus())
                || DiffCoverageStatusEnum.PARTIAL_COVERED.name().equals(r.getCoverageStatus())).count());
        report.setSuggestedRegressionModules(JsonUtil.toJson(objectMapper, risks.stream().map(AiTestopsDiffRiskItem::getAffectedModule).filter(Objects::nonNull).distinct().toList()));
        report.setReportSummary("Diff 分析识别风险 %d 项，准入结论 %s".formatted(risks.size(), gate.name()));
        report.setReportDetail(JsonUtil.toJson(objectMapper, Map.of("riskCount", risks.size())));
        report.setCreatedAt(report.getCreatedAt() == null ? LocalDateTime.now() : report.getCreatedAt());
        if (report.getId() == null) reportService.save(report); else reportService.updateById(report);
        update(new LambdaUpdateWrapper<AiTestopsDiffAnalysisTask>()
                .eq(AiTestopsDiffAnalysisTask::getId, taskId)
                .set(AiTestopsDiffAnalysisTask::getChangedFileCount, files.size())
                .set(AiTestopsDiffAnalysisTask::getHighRiskCount, report.getHighRiskCount())
                .set(AiTestopsDiffAnalysisTask::getMediumRiskCount, report.getMediumRiskCount())
                .set(AiTestopsDiffAnalysisTask::getLowRiskCount, report.getLowRiskCount())
                .set(AiTestopsDiffAnalysisTask::getNotCoveredRiskCount, report.getNotCoveredRiskCount())
                .set(AiTestopsDiffAnalysisTask::getMergeGateStatus, gate.name())
                .set(AiTestopsDiffAnalysisTask::getUpdatedAt, LocalDateTime.now()));
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

    private void validateCreateRequest(DiffAnalysisTaskCreateRequest request) {
        if (request == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "请求不能为空");
        if (!StringUtils.hasText(request.getDocumentId()) && !StringUtils.hasText(request.getRequirementExtractId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "documentId 和 requirementExtractId 至少传一个");
        }
        if (!StringUtils.hasText(request.getRepoUrl())) throw new BusinessException(ErrorCode.BAD_REQUEST, "仓库地址不能为空");
        if (!StringUtils.hasText(request.getSourceBranch())) throw new BusinessException(ErrorCode.BAD_REQUEST, "源分支不能为空");
        if (!StringUtils.hasText(request.getTargetBranch())) request.setTargetBranch("master");
        if (request.getSourceBranch().equals(request.getTargetBranch())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "源分支不能和目标分支相同");
        }
    }

    private DiffAnalysisOptions normalizeOptions(DiffAnalysisTaskCreateRequest request) {
        DiffAnalysisOptions options = request.getAnalysisOptions() == null ? new DiffAnalysisOptions() : request.getAnalysisOptions();
        options.setIncludeTestFiles(Boolean.TRUE.equals(options.getIncludeTestFiles()));
        options.setEnableAiAnalysis(options.getEnableAiAnalysis() == null || Boolean.TRUE.equals(options.getEnableAiAnalysis()));
        options.setEnableCoverageCheck(options.getEnableCoverageCheck() == null || Boolean.TRUE.equals(options.getEnableCoverageCheck()));
        options.setAutoGenerateSupplementCases(Boolean.TRUE.equals(options.getAutoGenerateSupplementCases()));
        return options;
    }

    private void assertNoRunningDuplicate(DiffAnalysisTaskCreateRequest request) {
        long count = count(new LambdaQueryWrapper<AiTestopsDiffAnalysisTask>()
                .eq(AiTestopsDiffAnalysisTask::getRepoUrl, request.getRepoUrl())
                .eq(AiTestopsDiffAnalysisTask::getSourceBranch, request.getSourceBranch())
                .eq(AiTestopsDiffAnalysisTask::getTargetBranch, request.getTargetBranch())
                .in(AiTestopsDiffAnalysisTask::getStatus, List.of(DiffAnalysisTaskStatusEnum.PENDING.name(), DiffAnalysisTaskStatusEnum.RUNNING.name())));
        if (count > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "同一仓库和分支存在运行中的 Diff 分析任务");
        }
    }

    @Transactional
    public AiTestopsDiffAnalysisTask createTaskAndSave(DiffAnalysisTaskCreateRequest request, DiffAnalysisOptions options) {
        AiTestopsDiffAnalysisTask task = createTask(request, options);
        save(task);
        return task;
    }

    private AiTestopsDiffAnalysisTask createTask(DiffAnalysisTaskCreateRequest request, DiffAnalysisOptions options) {
        LocalDateTime now = LocalDateTime.now();
        AiTestopsDiffAnalysisTask task = new AiTestopsDiffAnalysisTask();
        task.setTaskCode(IdGenerator.diffTaskCode());
        task.setDocumentId(request.getDocumentId());
        task.setRequirementExtractId(request.getRequirementExtractId());
        task.setRepoUrl(request.getRepoUrl());
        task.setRepoName(resolveRepoName(request.getRepoUrl()));
        task.setSourceBranch(request.getSourceBranch());
        task.setTargetBranch(request.getTargetBranch());
        task.setAnalysisOptions(JsonUtil.toJson(objectMapper, options));
        task.setStatus(DiffAnalysisTaskStatusEnum.PENDING.name());
        task.setChangedFileCount(0);
        task.setChangedMethodCount(null);
        task.setHighRiskCount(0);
        task.setMediumRiskCount(0);
        task.setLowRiskCount(0);
        task.setNotCoveredRiskCount(0);
        task.setCreatedBy(request.getCreatedBy());
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return task;
    }

    private void updateStatus(Long taskId, String status, String failReason) {
        update(new LambdaUpdateWrapper<AiTestopsDiffAnalysisTask>()
                .eq(AiTestopsDiffAnalysisTask::getId, taskId)
                .set(AiTestopsDiffAnalysisTask::getStatus, status)
                .set(AiTestopsDiffAnalysisTask::getFailReason, failReason)
                .set(AiTestopsDiffAnalysisTask::getUpdatedAt, LocalDateTime.now()));
    }

    private void saveAction(AiTestopsDiffRiskItem risk, String actionType, String before, String after,
                            String desc, String operator, String payload) {
        AiTestopsDiffRiskActionRecord record = new AiTestopsDiffRiskActionRecord();
        record.setRiskId(risk.getId());
        record.setTaskId(risk.getTaskId());
        record.setActionType(actionType);
        record.setBeforeStatus(before);
        record.setAfterStatus(after);
        record.setActionDesc(desc);
        record.setOperator(operator);
        record.setActionPayload(payload);
        record.setCreatedAt(LocalDateTime.now());
        actionRecordService.save(record);
    }

    private AiTestopsDiffAnalysisTask requireTask(Long taskId) {
        AiTestopsDiffAnalysisTask task = getById(taskId);
        if (task == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Diff 分析任务不存在: " + taskId);
        return task;
    }

    private AiTestopsDiffRiskItem requireRisk(Long riskId) {
        AiTestopsDiffRiskItem risk = riskItemService.getById(riskId);
        if (risk == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "风险不存在: " + riskId);
        return risk;
    }

    private DiffAnalysisTaskVO toTaskVO(AiTestopsDiffAnalysisTask task) {
        DiffAnalysisTaskVO vo = new DiffAnalysisTaskVO();
        vo.setTaskId(task.getId());
        vo.setTaskCode(task.getTaskCode());
        vo.setDocumentId(task.getDocumentId());
        vo.setRequirementExtractId(task.getRequirementExtractId());
        vo.setRepoUrl(task.getRepoUrl());
        vo.setRepoName(task.getRepoName());
        vo.setSourceBranch(task.getSourceBranch());
        vo.setTargetBranch(task.getTargetBranch());
        vo.setStatus(task.getStatus());
        vo.setFailReason(task.getFailReason());
        vo.setChangedFileCount(n(task.getChangedFileCount()));
        vo.setHighRiskCount(n(task.getHighRiskCount()));
        vo.setMediumRiskCount(n(task.getMediumRiskCount()));
        vo.setLowRiskCount(n(task.getLowRiskCount()));
        vo.setNotCoveredRiskCount(n(task.getNotCoveredRiskCount()));
        vo.setMergeGateStatus(task.getMergeGateStatus());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setUpdatedAt(task.getUpdatedAt());
        return vo;
    }

    private DiffChangedFileVO toFileVO(AiTestopsDiffChangedFile file) {
        DiffChangedFileVO vo = new DiffChangedFileVO();
        vo.setChangedFileId(file.getId());
        vo.setOldFilePath(file.getOldFilePath());
        vo.setNewFilePath(file.getNewFilePath());
        vo.setChangeType(file.getChangeType());
        vo.setLanguage(file.getLanguage());
        vo.setFileRole(file.getFileRole());
        vo.setAdditions(n(file.getAdditions()));
        vo.setDeletions(n(file.getDeletions()));
        vo.setChanges(n(file.getChanges()));
        vo.setPatch(file.getPatch());
        vo.setPatchSummary(file.getPatchSummary());
        vo.setTestFile(file.getIsTestFile() != null && file.getIsTestFile() == 1);
        vo.setInitialRiskLevel(file.getInitialRiskLevel());
        vo.setInitialRiskReason(file.getInitialRiskReason());
        return vo;
    }

    private DiffRiskItemVO toRiskVO(AiTestopsDiffRiskItem risk, List<AiTestopsDiffRiskCaseRel> relations, Map<Long, AiTestopsTestCase> testCaseMap) {
        DiffRiskItemVO vo = new DiffRiskItemVO();
        vo.setRiskId(risk.getId());
        vo.setRiskCode(risk.getRiskCode());
        vo.setRiskTitle(risk.getRiskTitle());
        vo.setRiskLevel(risk.getRiskLevel());
        vo.setRiskCategory(risk.getRiskCategory());
        vo.setSourceType(risk.getSourceType());
        vo.setAffectedModule(risk.getAffectedModule());
        vo.setAffectedScenarios(risk.getAffectedScenarios());
        vo.setRiskReason(risk.getRiskReason());
        vo.setTestSuggestion(risk.getTestSuggestion());
        vo.setMissingTestScenarios(risk.getMissingTestScenarios());
        vo.setCoverageStatus(risk.getCoverageStatus());
        vo.setCoverageReason(risk.getCoverageReason());
        vo.setProcessStatus(risk.getProcessStatus());
        vo.setMergeGateImpact(risk.getMergeGateImpact());
        vo.setMatchedCases(relations.stream().map(rel -> toRiskCaseRelVO(rel, testCaseMap)).toList());
        return vo;
    }

    private DiffRiskCaseRelVO toRiskCaseRelVO(AiTestopsDiffRiskCaseRel rel, Map<Long, AiTestopsTestCase> testCaseMap) {
        DiffRiskCaseRelVO vo = new DiffRiskCaseRelVO();
        vo.setId(rel.getId());
        vo.setCaseDbId(rel.getCaseId());
        AiTestopsTestCase testCase = rel.getCaseId() != null ? testCaseMap.get(rel.getCaseId()) : null;
        if (testCase != null) {
            vo.setTestCaseId(testCase.getTestCaseId());
            vo.setCaseId(testCase.getCaseId());
            vo.setCaseTitle(testCase.getTitle());
        }
        vo.setCoverageJudgement(rel.getCoverageJudgement());
        vo.setJudgementReason(rel.getJudgementReason());
        vo.setSimilarityScore(rel.getSimilarityScore());
        return vo;
    }

    private DiffMergeGateReportVO toReportVO(AiTestopsDiffMergeGateReport report) {
        if (report == null) return null;
        DiffMergeGateReportVO vo = new DiffMergeGateReportVO();
        vo.setReportId(report.getId());
        vo.setTaskId(report.getTaskId());
        vo.setReportCode(report.getReportCode());
        vo.setGateStatus(report.getGateStatus());
        vo.setGateReason(report.getGateReason());
        vo.setChangedFileCount(n(report.getChangedFileCount()));
        vo.setChangedMethodCount(n(report.getChangedMethodCount()));
        vo.setHighRiskCount(n(report.getHighRiskCount()));
        vo.setMediumRiskCount(n(report.getMediumRiskCount()));
        vo.setLowRiskCount(n(report.getLowRiskCount()));
        vo.setCoveredRiskCount(n(report.getCoveredRiskCount()));
        vo.setPartialCoveredRiskCount(n(report.getPartialCoveredRiskCount()));
        vo.setNotCoveredRiskCount(n(report.getNotCoveredRiskCount()));
        vo.setNeedConfirmRiskCount(n(report.getNeedConfirmRiskCount()));
        vo.setBlockedRiskCount(n(report.getBlockedRiskCount()));
        vo.setSuggestedCaseCount(n(report.getSuggestedCaseCount()));
        vo.setSuggestedRegressionModules(report.getSuggestedRegressionModules());
        vo.setReportSummary(report.getReportSummary());
        vo.setCreatedAt(report.getCreatedAt());
        return vo;
    }

    private String buildPatchSummary(AiTestopsDiffChangedFile file) {
        return "%s %s，新增 %d 行，删除 %d 行".formatted(file.getChangeType(), file.getNewFilePath(), n(file.getAdditions()), n(file.getDeletions()));
    }

    private boolean isCoreRole(String role) {
        return List.of(DiffFileRoleEnum.CONTROLLER.name(), DiffFileRoleEnum.SERVICE.name(), DiffFileRoleEnum.DAO.name(),
                DiffFileRoleEnum.SQL.name(), DiffFileRoleEnum.AUTH.name()).contains(role);
    }

    private int countRiskLevel(List<AiTestopsDiffRiskItem> risks, String level) {
        return (int) risks.stream().filter(item -> level.equals(item.getRiskLevel())).count();
    }

    private int countCoverage(List<AiTestopsDiffRiskItem> risks, String status) {
        return (int) risks.stream().filter(item -> status.equals(item.getCoverageStatus())).count();
    }

    private String buildGateReason(DiffMergeGateStatusEnum gate, List<AiTestopsDiffRiskItem> risks) {
        return switch (gate) {
            case PASS -> "所有风险均已覆盖或当前无风险项";
            case WARNING -> "存在中低风险未覆盖或部分覆盖风险，建议关注后合并";
            case BLOCK -> "存在高风险未覆盖、验证失败或阻塞风险，不建议合并";
            case MANUAL_REVIEW -> "存在覆盖关系无法自动判断的风险，需要人工评审";
        };
    }

    private String resolveRepoName(String repoUrl) {
        return GitDiffClient.resolveRepoName(repoUrl);
    }

    private int n(Integer value) {
        return value == null ? 0 : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
