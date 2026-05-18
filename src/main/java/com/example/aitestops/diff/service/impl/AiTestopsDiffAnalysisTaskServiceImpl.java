package com.example.aitestops.diff.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.aitestops.ai.client.AiClient;
import com.example.aitestops.ai.dto.AiChatRequest;
import com.example.aitestops.ai.dto.AiChatResponse;
import com.example.aitestops.ai.entity.AiTestopsGenerationRecord;
import com.example.aitestops.ai.entity.AiTestopsRequirementExtract;
import com.example.aitestops.ai.service.AiTestopsGenerationRecordService;
import com.example.aitestops.ai.service.AiTestopsRequirementExtractService;
import com.example.aitestops.common.config.AiModelProperties;
import com.example.aitestops.common.enums.GenerationStatusEnum;
import com.example.aitestops.common.enums.GenerationTypeEnum;
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
import com.example.aitestops.diff.service.DiffReportRefreshService;
import com.example.aitestops.diff.vo.DiffAnalysisReportVO;
import com.example.aitestops.diff.vo.DiffAnalysisSourceVO;
import com.example.aitestops.diff.vo.DiffAnalysisTaskVO;
import com.example.aitestops.diff.vo.DiffChangedFileVO;
import com.example.aitestops.diff.vo.DiffMergeGateReportVO;
import com.example.aitestops.diff.vo.DiffRiskCaseRelVO;
import com.example.aitestops.diff.vo.DiffRiskItemVO;
import com.example.aitestops.diff.vo.DiffSupplementCaseVO;
import com.example.aitestops.document.entity.AiTestopsDocument;
import com.example.aitestops.document.service.AiTestopsDocumentService;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Diff 分析任务服务实现。
 * <p>
 * 提供代码 Diff 分析的完整业务流程，包括：
 * <ul>
 *   <li>从 Git 仓库获取代码变更</li>
 *   <li>识别变更文件并分类</li>
 *   <li>基于规则和 AI 分析风险项</li>
 *   <li>匹配测试用例覆盖</li>
 *   <li>生成合并准入报告</li>
 *   <li>管理风险处理和验证流程</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiTestopsDiffAnalysisTaskServiceImpl
        extends ServiceImpl<AiTestopsDiffAnalysisTaskMapper, AiTestopsDiffAnalysisTask>
        implements AiTestopsDiffAnalysisTaskService {

    private static final Map<String, String> BUSINESS_TERM_MAP = Map.ofEntries(
            Map.entry("Order", "订单"),
            Map.entry("User", "用户"),
            Map.entry("Payment", "支付"),
            Map.entry("Product", "商品"),
            Map.entry("Diff", "Diff"),
            Map.entry("Report", "报告"),
            Map.entry("Refresh", "刷新"),
            Map.entry("Risk", "风险"),
            Map.entry("Coverage", "覆盖"),
            Map.entry("Analysis", "分析"),
            Map.entry("Task", "任务"),
            Map.entry("Case", "用例"),
            Map.entry("Supplement", "补充"),
            Map.entry("Merge", "合并"),
            Map.entry("Gate", "准入"),
            Map.entry("Change", "变更"),
            Map.entry("Changed", "变更"),
            Map.entry("File", "文件"),
            Map.entry("Document", "文档"),
            Map.entry("Requirement", "需求"),
            Map.entry("Export", "导出"),
            Map.entry("Generation", "生成"),
            Map.entry("Validation", "校验"),
            Map.entry("Review", "评审"),
            Map.entry("Branch", "分支"),
            Map.entry("Repository", "仓库"),
            Map.entry("Parser", "解析"),
            Map.entry("Chunk", "分块"),
            Map.entry("Upload", "上传"),
            Map.entry("Auth", "权限"),
            Map.entry("Login", "登录"),
            Map.entry("Role", "角色"),
            Map.entry("Permission", "权限"),
            Map.entry("Data", "数据"),
            Map.entry("Status", "状态"),
            Map.entry("Message", "消息"),
            Map.entry("Job", "调度")
    );

    private final GitDiffClient gitDiffClient;
    private final DiffFileClassifier fileClassifier;
    private final DiffRuleRiskService ruleRiskService;
    private final DiffCoverageMatcher coverageMatcher;
    private final AiTestopsDiffChangedFileService changedFileService;
    private final AiTestopsDiffRiskItemService riskItemService;
    private final AiTestopsDiffRiskCaseRelService riskCaseRelService;
    private final AiTestopsDiffRiskActionRecordService actionRecordService;
    private final AiTestopsDiffMergeGateReportService reportService;
    private final DiffReportRefreshService reportRefreshService;
    private final AiTestopsTestCaseService testCaseService;
    private final AiTestopsTestCaseDraftService draftService;
    private final AiTestopsGenerationRecordService generationRecordService;
    private final AiTestopsRequirementExtractService requirementExtractService;
    private final AiTestopsDocumentService documentService;
    private final AiClient aiClient;
    private final AiModelProperties aiModelProperties;
    private final ObjectMapper objectMapper;

    @Override
    public DiffAnalysisTaskVO createAndAnalyze(DiffAnalysisTaskCreateRequest request) {
        validateCreateRequest(request);
        resolveAnalysisSource(request);
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
    public List<DiffAnalysisSourceVO> listRecentSources(Integer limit) {
        int size = limit == null ? 10 : Math.max(1, Math.min(limit, 50));
        List<AiTestopsDocument> documents = documentService.list(new LambdaQueryWrapper<AiTestopsDocument>()
                .orderByDesc(AiTestopsDocument::getCreatedAt)
                .last("limit " + size));
        return documents.stream().map(document -> {
            AiTestopsRequirementExtract latestExtract = latestExtractByDocument(document.getDocumentId());
            return toSourceVO(document, latestExtract);
        }).toList();
    }

    @Override
    public List<String> listRepositoryBranches(String repoUrl) {
        return gitDiffClient.listBranches(repoUrl);
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
            case "LINK_CASE" -> {
                linkCasesToRisk(risk, request);
                risk.setCoverageStatus(DiffCoverageStatusEnum.COVERED.name());
                risk.setCoverageReason("人工关联已有正式用例");
                risk.setMergeGateImpact(resolveRiskGateImpact(risk));
                yield DiffRiskProcessStatusEnum.WAIT_TEST.name();
            }
            case "MARK_PASS" -> DiffRiskProcessStatusEnum.PASSED.name();
            case "MARK_FAIL" -> DiffRiskProcessStatusEnum.FAILED.name();
            case "MARK_BLOCKED" -> DiffRiskProcessStatusEnum.BLOCKED.name();
            case "CLOSE" -> DiffRiskProcessStatusEnum.CLOSED.name();
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的风险动作: " + actionType);
        };
        risk.setProcessStatus(after);
        risk.setUpdatedAt(LocalDateTime.now());
        riskItemService.updateById(risk);
        String payload = "LINK_CASE".equals(actionType) && request != null
                ? JsonUtil.toJson(objectMapper, Map.of("relatedCaseIds", request.getRelatedCaseIds()))
                : null;
        saveAction(risk, actionType, before, after, request == null ? null : request.getActionDesc(), request == null ? null : request.getOperator(), payload);
        refreshReport(risk.getTaskId());
    }

    @Override
    @Transactional
    public void verifyRisk(Long riskId, DiffRiskVerifyRequest request) {
        requireRisk(riskId);
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
        AiTestopsDiffAnalysisTask task = requireTask(risk.getTaskId());
        AiTestopsTestCaseDraft draft = createSupplementDraft(task, risk);

        String before = risk.getProcessStatus();
        LocalDateTime now = LocalDateTime.now();
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
        if (Boolean.TRUE.equals(options.getEnableAiAnalysis())) {
            recordAiRiskAnalysis(task, files, risks);
        }
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
                autoGenerateSupplementCaseIfNeeded(task, risk, options);
            }
        }
        refreshReport(task.getId());
        updateStatus(task.getId(), DiffAnalysisTaskStatusEnum.SUCCESS.name(), null);
    }

    private List<AiTestopsDiffChangedFile> saveChangedFiles(AiTestopsDiffAnalysisTask task, GitDiffResult diff, DiffAnalysisOptions options) {
        List<AiTestopsDiffChangedFile> files = new ArrayList<>();
        for (GitChangedFile changed : diff.changedFiles()) {
            String path = resolveChangedFilePath(changed);
            if (shouldIgnoreChangedFile(path)) {
                continue;
            }
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

    private String resolveChangedFilePath(GitChangedFile changed) {
        if (changed == null) {
            return null;
        }
        return StringUtils.hasText(changed.newFilePath()) ? changed.newFilePath() : changed.oldFilePath();
    }

    private boolean shouldIgnoreChangedFile(String path) {
        if (!StringUtils.hasText(path)) {
            return true;
        }
        String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        return normalized.startsWith("docs/") || normalized.contains("/docs/");
    }

    private void linkCasesToRisk(AiTestopsDiffRiskItem risk, DiffRiskActionRequest request) {
        Set<Long> requestedCaseIds = request == null || request.getRelatedCaseIds() == null
                ? Set.of()
                : request.getRelatedCaseIds().stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (requestedCaseIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "LINK_CASE 必须提供 relatedCaseIds");
        }
        List<AiTestopsTestCase> cases = testCaseService.listByIds(requestedCaseIds);
        if (cases.size() != requestedCaseIds.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "存在无效的正式用例 ID");
        }
        List<String> inactiveCaseIds = cases.stream()
                .filter(testCase -> !"ACTIVE".equals(testCase.getStatus()))
                .map(AiTestopsTestCase::getTestCaseId)
                .toList();
        if (!inactiveCaseIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只能关联 ACTIVE 状态的正式用例: " + String.join(",", inactiveCaseIds));
        }
        Set<Long> existingCaseIds = riskCaseRelService.list(new LambdaQueryWrapper<AiTestopsDiffRiskCaseRel>()
                        .eq(AiTestopsDiffRiskCaseRel::getRiskId, risk.getId())
                        .in(AiTestopsDiffRiskCaseRel::getCaseId, requestedCaseIds))
                .stream()
                .map(AiTestopsDiffRiskCaseRel::getCaseId)
                .collect(Collectors.toSet());
        LocalDateTime now = LocalDateTime.now();
        List<AiTestopsDiffRiskCaseRel> relations = cases.stream()
                .filter(testCase -> !existingCaseIds.contains(testCase.getId()))
                .map(testCase -> {
                    AiTestopsDiffRiskCaseRel rel = new AiTestopsDiffRiskCaseRel();
                    rel.setRiskId(risk.getId());
                    rel.setCaseId(testCase.getId());
                    rel.setDocumentId(testCase.getDocumentId());
                    rel.setRequirementExtractId(testCase.getRequirementExtractId());
                    rel.setCaseSourceType("FORMAL");
                    rel.setRelationType("MANUAL_LINKED_CASE");
                    rel.setCoverageJudgement(DiffCoverageStatusEnum.COVERED.name());
                    rel.setJudgementReason("人工关联已有正式用例");
                    rel.setSimilarityScore(BigDecimal.ONE);
                    rel.setGeneratedFromAi(0);
                    rel.setCreatedBy(request == null ? null : request.getOperator());
                    rel.setCreatedAt(now);
                    return rel;
                })
                .toList();
        if (!relations.isEmpty()) {
            riskCaseRelService.saveBatch(relations);
        }
    }

    private void autoGenerateSupplementCaseIfNeeded(AiTestopsDiffAnalysisTask task, AiTestopsDiffRiskItem risk, DiffAnalysisOptions options) {
        if (!Boolean.TRUE.equals(options.getAutoGenerateSupplementCases())) {
            return;
        }
        if (!DiffCoverageStatusEnum.NOT_COVERED.name().equals(risk.getCoverageStatus())
                && !DiffCoverageStatusEnum.PARTIAL_COVERED.name().equals(risk.getCoverageStatus())) {
            return;
        }
        AiTestopsTestCaseDraft draft = createSupplementDraft(task, risk);
        String before = risk.getProcessStatus();
        risk.setProcessStatus(DiffRiskProcessStatusEnum.WAIT_TEST.name());
        risk.setUpdatedAt(LocalDateTime.now());
        riskItemService.updateById(risk);
        saveAction(risk, "AUTO_GENERATE_CASE", before, risk.getProcessStatus(), "自动生成 Diff 补充用例草稿", "system",
                JsonUtil.toJson(objectMapper, Map.of("draftCaseId", draft.getDraftCaseId())));
    }

    private AiTestopsTestCaseDraft createSupplementDraft(AiTestopsDiffAnalysisTask task, AiTestopsDiffRiskItem risk) {
        LocalDateTime now = LocalDateTime.now();
        AiTestopsTestCaseDraft draft = new AiTestopsTestCaseDraft();
        draft.setDraftCaseId(IdGenerator.draftCaseId());
        draft.setCaseId("DIFF_" + risk.getRiskCode());
        draft.setGenerationId("DIFF_SUPPLEMENT_" + risk.getId() + "_" + now.toString().replace(":", "").replace("-", ""));
        draft.setDocumentId(task.getDocumentId());
        draft.setRequirementExtractId(task.getRequirementExtractId());
        draft.setTitle(buildSupplementCaseTitle(risk));
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
        return draft;
    }

    private void recordAiRiskAnalysis(AiTestopsDiffAnalysisTask task, List<AiTestopsDiffChangedFile> files,
                                      List<AiTestopsDiffRiskItem> ruleRisks) {
        String generationId = IdGenerator.generationId();
        Map<String, Object> inputSnapshot = new LinkedHashMap<>();
        inputSnapshot.put("taskCode", task.getTaskCode());
        inputSnapshot.put("documentId", task.getDocumentId());
        inputSnapshot.put("requirementExtractId", task.getRequirementExtractId());
        inputSnapshot.put("repoName", task.getRepoName());
        inputSnapshot.put("sourceBranch", task.getSourceBranch());
        inputSnapshot.put("targetBranch", task.getTargetBranch());
        inputSnapshot.put("changedFiles", files.stream().map(file -> Map.of(
                "path", file.getNewFilePath(),
                "changeType", file.getChangeType(),
                "fileRole", file.getFileRole(),
                "additions", n(file.getAdditions()),
                "deletions", n(file.getDeletions()),
                "patchSummary", nullToEmpty(file.getPatchSummary())
        )).toList());
        inputSnapshot.put("ruleRisks", ruleRisks.stream().map(risk -> Map.of(
                "riskCode", risk.getRiskCode(),
                "riskTitle", risk.getRiskTitle(),
                "riskLevel", risk.getRiskLevel(),
                "affectedModule", nullToEmpty(risk.getAffectedModule()),
                "riskReason", nullToEmpty(risk.getRiskReason())
        )).toList());

        LocalDateTime now = LocalDateTime.now();
        AiTestopsGenerationRecord record = new AiTestopsGenerationRecord();
        record.setGenerationId(generationId);
        record.setDocumentId(task.getDocumentId());
        record.setRequirementExtractId(task.getRequirementExtractId());
        record.setPromptTemplateCode("DIFF_RISK_ANALYSIS_BUILTIN");
        record.setPromptTemplateVersion("v1");
        record.setModelCode(aiModelProperties.getModelCode());
        record.setModelName(aiModelProperties.getModelName());
        record.setGenerationType(GenerationTypeEnum.DIFF_RISK_ANALYSIS.name());
        record.setInputSnapshotJson(JsonUtil.toJson(objectMapper, inputSnapshot));
        record.setStatus(GenerationStatusEnum.PROCESSING.name());
        record.setStartedAt(now);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        generationRecordService.save(record);

        try {
            AiChatResponse response = aiClient.chat(AiChatRequest.builder()
                    .modelCode(aiModelProperties.getModelCode())
                    .modelName(aiModelProperties.getModelName())
                    .systemPrompt("你是代码 Diff 测试覆盖风险分析助手。请基于变更文件和规则风险输出风险补充建议 JSON。")
                    .userPrompt(JsonUtil.toJson(objectMapper, inputSnapshot))
                    .generationType(GenerationTypeEnum.DIFF_RISK_ANALYSIS.name())
                    .build());
            generationRecordService.update(new LambdaUpdateWrapper<AiTestopsGenerationRecord>()
                    .eq(AiTestopsGenerationRecord::getGenerationId, generationId)
                    .set(AiTestopsGenerationRecord::getOutputJson, response.getRawResponseJson())
                    .set(AiTestopsGenerationRecord::getStatus, GenerationStatusEnum.SUCCESS.name())
                    .set(AiTestopsGenerationRecord::getErrorMessage, null)
                    .set(AiTestopsGenerationRecord::getTokenInput, response.getPromptTokens())
                    .set(AiTestopsGenerationRecord::getTokenOutput, response.getCompletionTokens())
                    .set(AiTestopsGenerationRecord::getModelName, response.getModelName())
                    .set(AiTestopsGenerationRecord::getFinishedAt, LocalDateTime.now())
                    .set(AiTestopsGenerationRecord::getUpdatedAt, LocalDateTime.now()));
        } catch (Exception ex) {
            log.warn("Diff AI 风险分析失败，保留规则风险: taskId={}, generationId={}", task.getId(), generationId, ex);
            generationRecordService.update(new LambdaUpdateWrapper<AiTestopsGenerationRecord>()
                    .eq(AiTestopsGenerationRecord::getGenerationId, generationId)
                    .set(AiTestopsGenerationRecord::getStatus, GenerationStatusEnum.FAILED.name())
                    .set(AiTestopsGenerationRecord::getErrorMessage, truncate(ex.getMessage(), 1000))
                    .set(AiTestopsGenerationRecord::getFinishedAt, LocalDateTime.now())
                    .set(AiTestopsGenerationRecord::getUpdatedAt, LocalDateTime.now()));
        }
    }

    private void refreshReport(Long taskId) {
        reportRefreshService.refresh(taskId);
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
        if (!StringUtils.hasText(request.getRepoUrl())) throw new BusinessException(ErrorCode.BAD_REQUEST, "仓库地址不能为空");
        if (!StringUtils.hasText(request.getSourceBranch())) throw new BusinessException(ErrorCode.BAD_REQUEST, "源分支不能为空");
        if (!StringUtils.hasText(request.getTargetBranch())) request.setTargetBranch("master");
        if (request.getSourceBranch().equals(request.getTargetBranch())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "源分支不能和目标分支相同");
        }
    }

    private void resolveAnalysisSource(DiffAnalysisTaskCreateRequest request) {
        String documentId = trimToNull(request.getDocumentId());
        String requirementExtractId = trimToNull(request.getRequirementExtractId());

        if (requirementExtractId != null) {
            AiTestopsRequirementExtract extract = requireRequirementExtract(requirementExtractId);
            if (documentId != null && !documentId.equals(extract.getDocumentId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "需求解析结果不属于关联文档: requirementExtractId=" + requirementExtractId + ", documentId=" + documentId);
            }
            request.setDocumentId(extract.getDocumentId());
            request.setRequirementExtractId(extract.getRequirementExtractId());
            return;
        }

        if (documentId != null) {
            AiTestopsDocument document = requireDocument(documentId);
            AiTestopsRequirementExtract latestExtract = latestExtractByDocument(document.getDocumentId());
            request.setDocumentId(document.getDocumentId());
            request.setRequirementExtractId(latestExtract == null ? null : latestExtract.getRequirementExtractId());
            return;
        }

        AiTestopsRequirementExtract latestExtract = latestRequirementExtract();
        if (latestExtract != null) {
            request.setDocumentId(latestExtract.getDocumentId());
            request.setRequirementExtractId(latestExtract.getRequirementExtractId());
            return;
        }

        AiTestopsDocument latestDocument = latestDocument();
        if (latestDocument != null) {
            request.setDocumentId(latestDocument.getDocumentId());
            request.setRequirementExtractId(null);
            return;
        }

        throw new BusinessException(ErrorCode.BAD_REQUEST, "请先创建并解析需求文档，或手动传入 documentId/requirementExtractId");
    }

    private AiTestopsDocument requireDocument(String documentId) {
        AiTestopsDocument document = documentService.getOne(new LambdaQueryWrapper<AiTestopsDocument>()
                .eq(AiTestopsDocument::getDocumentId, documentId)
                .last("limit 1"), false);
        if (document == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND, "文档不存在: " + documentId);
        }
        return document;
    }

    private AiTestopsRequirementExtract requireRequirementExtract(String requirementExtractId) {
        AiTestopsRequirementExtract extract = requirementExtractService.getOne(new LambdaQueryWrapper<AiTestopsRequirementExtract>()
                .eq(AiTestopsRequirementExtract::getRequirementExtractId, requirementExtractId)
                .last("limit 1"), false);
        if (extract == null) {
            throw new BusinessException(ErrorCode.REQUIREMENT_EXTRACT_NOT_FOUND, "需求解析结果不存在: " + requirementExtractId);
        }
        return extract;
    }

    private AiTestopsRequirementExtract latestExtractByDocument(String documentId) {
        return requirementExtractService.getOne(new LambdaQueryWrapper<AiTestopsRequirementExtract>()
                .eq(AiTestopsRequirementExtract::getDocumentId, documentId)
                .orderByDesc(AiTestopsRequirementExtract::getCreatedAt)
                .last("limit 1"), false);
    }

    private AiTestopsRequirementExtract latestRequirementExtract() {
        return requirementExtractService.getOne(new LambdaQueryWrapper<AiTestopsRequirementExtract>()
                .orderByDesc(AiTestopsRequirementExtract::getCreatedAt)
                .last("limit 1"), false);
    }

    private AiTestopsDocument latestDocument() {
        return documentService.getOne(new LambdaQueryWrapper<AiTestopsDocument>()
                .orderByDesc(AiTestopsDocument::getCreatedAt)
                .last("limit 1"), false);
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
        task.setChangedMethodCount(0);
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

    private DiffAnalysisSourceVO toSourceVO(AiTestopsDocument document, AiTestopsRequirementExtract extract) {
        DiffAnalysisSourceVO vo = new DiffAnalysisSourceVO();
        vo.setDocumentId(document.getDocumentId());
        vo.setDocumentTitle(document.getTitle());
        vo.setParseStatus(document.getParseStatus());
        vo.setDocumentCreatedAt(document.getCreatedAt());
        if (extract != null) {
            vo.setRequirementExtractId(extract.getRequirementExtractId());
            vo.setGenerationId(extract.getGenerationId());
            vo.setRequirementExtractCreatedAt(extract.getCreatedAt());
        }
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

    private String buildSupplementCaseTitle(AiTestopsDiffRiskItem risk) {
        String subject = toBusinessSubject(risk.getAffectedModule());
        String scenario = toSupplementScenario(risk.getRiskCategory());
        if (StringUtils.hasText(subject) && StringUtils.hasText(scenario)) {
            return truncate("验证" + subject + scenario, 255);
        }
        if (StringUtils.hasText(subject)) {
            return truncate("验证" + subject + "变更回归", 255);
        }
        if (StringUtils.hasText(risk.getRiskCategory())) {
            return truncate("验证" + risk.getRiskCategory() + "变更后的业务流程", 255);
        }
        return "验证变更影响业务流程";
    }

    private String toSupplementScenario(String riskCategory) {
        if (!StringUtils.hasText(riskCategory)) {
            return "变更回归";
        }
        return switch (riskCategory) {
            case "业务逻辑" -> "核心流程回归";
            case "接口兼容" -> "接口入参出参兼容";
            case "数据一致性" -> "数据读写一致性";
            case "数据结构" -> "数据结构兼容与回滚";
            case "配置风险" -> "环境配置生效";
            case "权限风险" -> "权限控制";
            case "异步消息" -> "消息消费幂等";
            case "调度风险" -> "定时任务执行";
            case "代码变更" -> "基础功能回归";
            default -> riskCategory + "回归";
        };
    }

    private String toBusinessSubject(String affectedModule) {
        if (!StringUtils.hasText(affectedModule)) {
            return "";
        }
        String name = affectedModule.replace('\\', '/');
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }
        int extension = name.lastIndexOf('.');
        if (extension > 0) {
            name = name.substring(0, extension);
        }
        name = removeTechnicalSuffixes(name);
        List<String> words = splitIdentifierWords(name);
        if (words.isEmpty()) {
            return name;
        }
        StringBuilder subject = new StringBuilder();
        for (String word : words) {
            subject.append(BUSINESS_TERM_MAP.getOrDefault(word, word));
        }
        return subject.toString();
    }

    private String removeTechnicalSuffixes(String name) {
        String result = name;
        String previous;
        do {
            previous = result;
            result = result.replaceFirst("(ServiceImpl|Controller|Service|Mapper|Repository|Configuration|Config|DTO|VO|Entity|Request|Response|Client|Calculator|Classifier|Linker)$", "");
        } while (!result.equals(previous));
        return result;
    }

    private List<String> splitIdentifierWords(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        String normalized = value
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")
                .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replaceAll("[^A-Za-z0-9]+", " ")
                .trim();
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }
        return List.of(normalized.split("\\s+"));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
