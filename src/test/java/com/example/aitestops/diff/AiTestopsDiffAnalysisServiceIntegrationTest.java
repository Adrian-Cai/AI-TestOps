package com.example.aitestops.diff;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aitestops.ai.entity.AiTestopsGenerationRecord;
import com.example.aitestops.ai.entity.AiTestopsRequirementExtract;
import com.example.aitestops.ai.service.AiTestopsGenerationRecordService;
import com.example.aitestops.ai.service.AiTestopsRequirementExtractService;
import com.example.aitestops.common.enums.GenerationTypeEnum;
import com.example.aitestops.common.exception.BusinessException;
import com.example.aitestops.diff.dto.DiffAnalysisOptions;
import com.example.aitestops.diff.dto.DiffAnalysisTaskCreateRequest;
import com.example.aitestops.diff.dto.DiffRiskActionRequest;
import com.example.aitestops.diff.service.AiTestopsDiffAnalysisTaskService;
import com.example.aitestops.diff.vo.DiffAnalysisReportVO;
import com.example.aitestops.diff.vo.DiffAnalysisSourceVO;
import com.example.aitestops.diff.vo.DiffAnalysisTaskVO;
import com.example.aitestops.diff.vo.DiffSupplementCaseVO;
import com.example.aitestops.document.entity.AiTestopsDocument;
import com.example.aitestops.document.service.AiTestopsDocumentService;
import com.example.aitestops.testcase.dto.TestCaseDraftReviewRequest;
import com.example.aitestops.testcase.entity.AiTestopsTestCase;
import com.example.aitestops.testcase.entity.AiTestopsTestCaseDraft;
import com.example.aitestops.testcase.service.AiTestopsTestCaseDraftService;
import com.example.aitestops.testcase.service.AiTestopsTestCaseService;
import com.example.aitestops.testcase.vo.TestCaseVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AiTestopsDiffAnalysisServiceIntegrationTest {

    @Autowired
    private AiTestopsDiffAnalysisTaskService diffAnalysisTaskService;

    @Autowired
    private AiTestopsTestCaseService testCaseService;

    @Autowired
    private AiTestopsTestCaseDraftService draftService;

    @Autowired
    private AiTestopsGenerationRecordService generationRecordService;

    @Autowired
    private AiTestopsDocumentService documentService;

    @Autowired
    private AiTestopsRequirementExtractService requirementExtractService;

    @TempDir
    Path tempDir;

    @Test
    void createAndAnalyzeShouldPersistChangedFilesRisksCoverageAndGateReport() throws Exception {
        Path repo = createLocalRepositoryWithFeatureDiff("repo-basic");
        saveFormalCase("TCDB_DIFF_001", "DOC_DIFF_001", "REXT_DIFF_001",
                "OrderService Service business logic change regression", "[\"business logic\",\"Service\",\"OrderService\"]");

        DiffAnalysisTaskCreateRequest request = baseRequest(repo, "DOC_DIFF_001", "REXT_DIFF_001");

        DiffAnalysisTaskVO task = diffAnalysisTaskService.createAndAnalyze(request);
        DiffAnalysisReportVO report = diffAnalysisTaskService.getReport(task.getTaskId());

        assertThat(task.getStatus()).isEqualTo("SUCCESS");
        assertThat(report.getChangedFiles()).hasSize(1);
        assertThat(report.getChangedFiles().get(0).getFileRole()).isEqualTo("SERVICE");
        assertThat(report.getReport().getChangedMethodCount()).isZero();
        assertThat(report.getRiskList()).hasSize(1);
        assertThat(report.getRiskList().get(0).getRiskLevel()).isEqualTo("HIGH");
        assertThat(report.getReport()).isNotNull();
        assertThat(report.getReport().getGateStatus()).isIn("PASS", "WARNING", "BLOCK", "MANUAL_REVIEW");
    }

    @Test
    void createAndAnalyzeShouldIgnoreDocsDirectoryChangedFiles() throws Exception {
        Path repo = createLocalRepositoryWithDocsAndFeatureDiff("repo-ignore-docs");
        saveFormalCase("TCDB_DIFF_DOCS_IGNORE", "DOC_DIFF_DOCS_IGNORE", "REXT_DIFF_DOCS_IGNORE",
                "OrderService Service business logic change regression", "[\"business logic\",\"Service\",\"OrderService\"]");

        DiffAnalysisTaskCreateRequest request = baseRequest(repo, "DOC_DIFF_DOCS_IGNORE", "REXT_DIFF_DOCS_IGNORE");

        DiffAnalysisReportVO report = diffAnalysisTaskService.getReport(
                diffAnalysisTaskService.createAndAnalyze(request).getTaskId());

        assertThat(report.getChangedFiles()).hasSize(1);
        assertThat(report.getChangedFiles().get(0).getNewFilePath()).doesNotContain("docs/");
        assertThat(report.getChangedFiles().get(0).getFileRole()).isEqualTo("SERVICE");
        assertThat(report.getReport().getChangedFileCount()).isEqualTo(1);
    }

    @Test
    void createAndAnalyzeShouldMatchCasesByDocumentWhenRequirementIdDiffers() throws Exception {
        Path repo = createLocalRepositoryWithFeatureDiff("repo-document-fallback");
        saveFormalCase("TCDB_DIFF_DOC_ONLY", "DOC_DIFF_DOC_ONLY", "REXT_OLD",
                "OrderService Service file change risk regression", "[\"Service\",\"OrderService\",\"file change\"]");
        DiffAnalysisTaskCreateRequest request = baseRequest(repo, "DOC_DIFF_DOC_ONLY", null);

        DiffAnalysisReportVO report = diffAnalysisTaskService.getReport(
                diffAnalysisTaskService.createAndAnalyze(request).getTaskId());

        assertThat(report.getRiskList()).hasSize(1);
        assertThat(report.getRiskList().get(0).getMatchedCases()).isNotEmpty();
        assertThat(report.getRiskList().get(0).getCoverageStatus()).isIn("COVERED", "PARTIAL_COVERED");
    }

    @Test
    void linkCaseActionShouldCreateRiskCaseRelationAndUpdateCoverage() throws Exception {
        Path repo = createLocalRepositoryWithFeatureDiff("repo-link-case");
        AiTestopsTestCase testCase = saveFormalCase("TCDB_DIFF_LINK", "DOC_DIFF_LINK", "REXT_DIFF_LINK",
                "Manual regression case", "[\"manual\"]");
        DiffAnalysisTaskCreateRequest request = baseRequest(repo, "DOC_DIFF_LINK", "REXT_DIFF_LINK");
        request.getAnalysisOptions().setEnableCoverageCheck(false);

        DiffAnalysisReportVO report = diffAnalysisTaskService.getReport(diffAnalysisTaskService.createAndAnalyze(request).getTaskId());
        Long riskId = report.getRiskList().get(0).getRiskId();
        DiffRiskActionRequest actionRequest = new DiffRiskActionRequest();
        actionRequest.setActionType("LINK_CASE");
        actionRequest.setRelatedCaseIds(List.of(testCase.getId()));
        actionRequest.setOperator("qa");

        diffAnalysisTaskService.applyRiskAction(riskId, actionRequest);
        DiffAnalysisReportVO updated = diffAnalysisTaskService.getReport(report.getTask().getTaskId());

        assertThat(updated.getRiskList().get(0).getCoverageStatus()).isEqualTo("COVERED");
        assertThat(updated.getRiskList().get(0).getProcessStatus()).isEqualTo("WAIT_TEST");
        assertThat(updated.getRiskList().get(0).getMatchedCases())
                .extracting("caseDbId")
                .contains(testCase.getId());
    }

    @Test
    void autoGenerateSupplementCasesShouldCreateDraftForUncoveredRisk() throws Exception {
        Path repo = createLocalRepositoryWithFeatureDiff("repo-auto-supplement");
        saveDocument("DOC_DIFF_AUTO_CASE", "自动补充用例需求");
        saveRequirementExtract("REXT_DIFF_AUTO_CASE", "DOC_DIFF_AUTO_CASE", LocalDateTime.now());
        DiffAnalysisTaskCreateRequest request = baseRequest(repo, "DOC_DIFF_AUTO_CASE", "REXT_DIFF_AUTO_CASE");
        request.getAnalysisOptions().setAutoGenerateSupplementCases(true);

        DiffAnalysisReportVO report = diffAnalysisTaskService.getReport(diffAnalysisTaskService.createAndAnalyze(request).getTaskId());
        long draftCount = draftService.count(new LambdaQueryWrapper<AiTestopsTestCaseDraft>()
                .eq(AiTestopsTestCaseDraft::getDocumentId, "DOC_DIFF_AUTO_CASE")
                .eq(AiTestopsTestCaseDraft::getCaseType, "DIFF_SUPPLEMENT"));
        AiTestopsTestCaseDraft draft = draftService.getOne(new LambdaQueryWrapper<AiTestopsTestCaseDraft>()
                .eq(AiTestopsTestCaseDraft::getDocumentId, "DOC_DIFF_AUTO_CASE")
                .eq(AiTestopsTestCaseDraft::getCaseType, "DIFF_SUPPLEMENT")
                .last("limit 1"));

        assertThat(report.getRiskList()).hasSize(1);
        assertThat(report.getRiskList().get(0).getCoverageStatus()).isEqualTo("NOT_COVERED");
        assertThat(report.getRiskList().get(0).getProcessStatus()).isEqualTo("WAIT_TEST");
        assertThat(draftCount).isEqualTo(1);
        assertThat(draft.getTitle()).isEqualTo("验证订单核心流程回归");
        assertThat(draft.getTitle()).doesNotContain("文件变更风险", "src/main/java", "OrderService.java");
    }

    @Test
    void approvingDiffSupplementDraftShouldLinkFormalCaseBackToRisk() throws Exception {
        Path repo = createLocalRepositoryWithFeatureDiff("repo-approve-supplement-link");
        saveDocument("DOC_DIFF_APPROVE_SUPPLEMENT", "Diff supplement relation requirement");
        saveRequirementExtract("REXT_DIFF_APPROVE_SUPPLEMENT", "DOC_DIFF_APPROVE_SUPPLEMENT", LocalDateTime.now());
        DiffAnalysisTaskCreateRequest request = baseRequest(repo, "DOC_DIFF_APPROVE_SUPPLEMENT", "REXT_DIFF_APPROVE_SUPPLEMENT");
        DiffAnalysisReportVO report = diffAnalysisTaskService.getReport(
                diffAnalysisTaskService.createAndAnalyze(request).getTaskId());
        Long riskId = report.getRiskList().get(0).getRiskId();

        DiffSupplementCaseVO supplement = diffAnalysisTaskService.generateSupplementCases(riskId);
        TestCaseDraftReviewRequest approveRequest = new TestCaseDraftReviewRequest();
        approveRequest.setReviewer("qa");
        TestCaseVO approved = draftService.approveDraft(
                supplement.getGeneratedCases().get(0).getDraftCaseId(),
                approveRequest);
        DiffAnalysisReportVO updated = diffAnalysisTaskService.getReport(report.getTask().getTaskId());

        assertThat(updated.getRiskList().get(0).getCoverageStatus()).isEqualTo("COVERED");
        assertThat(updated.getRiskList().get(0).getMatchedCases())
                .extracting("caseId")
                .contains(approved.getCaseId());
        assertThat(updated.getReport().getCoveredRiskCount()).isEqualTo(1);
        assertThat(updated.getReport().getNotCoveredRiskCount()).isZero();
    }

    @Test
    void enableAiAnalysisShouldPersistGenerationRecordWithoutChangingRuleRiskFlow() throws Exception {
        Path repo = createLocalRepositoryWithFeatureDiff("repo-ai-analysis-record");
        saveDocument("DOC_DIFF_AI", "AI 风险分析需求");
        saveRequirementExtract("REXT_DIFF_AI", "DOC_DIFF_AI", LocalDateTime.now());
        DiffAnalysisTaskCreateRequest request = baseRequest(repo, "DOC_DIFF_AI", "REXT_DIFF_AI");
        request.getAnalysisOptions().setEnableAiAnalysis(true);

        DiffAnalysisTaskVO task = diffAnalysisTaskService.createAndAnalyze(request);
        long generationCount = generationRecordService.count(new LambdaQueryWrapper<AiTestopsGenerationRecord>()
                .eq(AiTestopsGenerationRecord::getDocumentId, "DOC_DIFF_AI")
                .eq(AiTestopsGenerationRecord::getGenerationType, GenerationTypeEnum.DIFF_RISK_ANALYSIS.name()));

        assertThat(task.getStatus()).isEqualTo("SUCCESS");
        assertThat(generationCount).isEqualTo(1);
    }

    @Test
    void createAndAnalyzeShouldUseLatestRequirementExtractWhenSourceIdsAreMissing() throws Exception {
        Path repo = createLocalRepositoryWithFeatureDiff("repo-latest-source");
        saveDocument("DOC_DIFF_LATEST", "最近需求文档");
        saveRequirementExtract("REXT_DIFF_LATEST", "DOC_DIFF_LATEST", LocalDateTime.now().plusDays(1));
        DiffAnalysisTaskCreateRequest request = baseRequest(repo, null, null);

        DiffAnalysisTaskVO task = diffAnalysisTaskService.createAndAnalyze(request);

        assertThat(task.getStatus()).isEqualTo("SUCCESS");
        assertThat(task.getDocumentId()).isEqualTo("DOC_DIFF_LATEST");
        assertThat(task.getRequirementExtractId()).isEqualTo("REXT_DIFF_LATEST");
    }

    @Test
    void createAndAnalyzeShouldRejectRequirementExtractFromDifferentDocument() throws Exception {
        Path repo = createLocalRepositoryWithFeatureDiff("repo-mismatch-source");
        saveDocument("DOC_DIFF_SOURCE_A", "文档 A");
        saveDocument("DOC_DIFF_SOURCE_B", "文档 B");
        saveRequirementExtract("REXT_DIFF_SOURCE_B", "DOC_DIFF_SOURCE_B", LocalDateTime.now());
        DiffAnalysisTaskCreateRequest request = baseRequest(repo, "DOC_DIFF_SOURCE_A", "REXT_DIFF_SOURCE_B");

        assertThatThrownBy(() -> diffAnalysisTaskService.createAndAnalyze(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("需求解析结果不属于关联文档");
    }

    @Test
    void listRecentSourcesShouldReturnDocumentsWithLatestRequirementExtract() {
        saveDocument("DOC_DIFF_RECENT", "最近来源");
        saveRequirementExtract("REXT_DIFF_OLD", "DOC_DIFF_RECENT", LocalDateTime.now().minusHours(1));
        saveRequirementExtract("REXT_DIFF_NEW", "DOC_DIFF_RECENT", LocalDateTime.now());

        List<DiffAnalysisSourceVO> sources = diffAnalysisTaskService.listRecentSources(5);

        assertThat(sources).anySatisfy(source -> {
            assertThat(source.getDocumentId()).isEqualTo("DOC_DIFF_RECENT");
            assertThat(source.getRequirementExtractId()).isEqualTo("REXT_DIFF_NEW");
        });
    }

    @Test
    void listRepositoryBranchesShouldReturnLocalRepositoryBranches() throws Exception {
        Path repo = createLocalRepositoryWithFeatureDiff("repo-branches");

        List<String> branches = diffAnalysisTaskService.listRepositoryBranches(repo.toString());

        assertThat(branches).contains("master", "feature/diff-risk");
    }

    private DiffAnalysisTaskCreateRequest baseRequest(Path repo, String documentId, String requirementExtractId) {
        DiffAnalysisTaskCreateRequest request = new DiffAnalysisTaskCreateRequest();
        request.setDocumentId(documentId);
        request.setRequirementExtractId(requirementExtractId);
        request.setRepoUrl(repo.toString());
        request.setSourceBranch("feature/diff-risk");
        request.setTargetBranch("master");
        DiffAnalysisOptions options = new DiffAnalysisOptions();
        options.setEnableCoverageCheck(true);
        options.setEnableAiAnalysis(false);
        request.setAnalysisOptions(options);
        return request;
    }

    private Path createLocalRepositoryWithFeatureDiff(String repoName) throws Exception {
        Path repo = tempDir.resolve(repoName);
        Files.createDirectories(repo);
        run(repo.getParent(), "git", "init", "-b", "master", repo.toString());
        run(repo, "git", "config", "user.email", "qa@example.com");
        run(repo, "git", "config", "user.name", "qa");
        Path service = repo.resolve("src/main/java/demo/OrderService.java");
        Files.createDirectories(service.getParent());
        Files.writeString(service, "package demo;\npublic class OrderService { public String status(){ return \"old\"; } }\n", StandardCharsets.UTF_8);
        run(repo, "git", "add", ".");
        run(repo, "git", "commit", "-m", "initial");
        run(repo, "git", "checkout", "-b", "feature/diff-risk");
        Files.writeString(service, "package demo;\npublic class OrderService { public String status(){ return \"new\"; } }\n", StandardCharsets.UTF_8);
        run(repo, "git", "add", ".");
        run(repo, "git", "commit", "-m", "change service");
        return repo;
    }

    private Path createLocalRepositoryWithDocsAndFeatureDiff(String repoName) throws Exception {
        Path repo = tempDir.resolve(repoName);
        Files.createDirectories(repo);
        run(repo.getParent(), "git", "init", "-b", "master", repo.toString());
        run(repo, "git", "config", "user.email", "qa@example.com");
        run(repo, "git", "config", "user.name", "qa");

        Path service = repo.resolve("src/main/java/demo/OrderService.java");
        Path docs = repo.resolve("docs/guide/change-log.md");
        Files.createDirectories(service.getParent());
        Files.createDirectories(docs.getParent());
        Files.writeString(service, "package demo;\npublic class OrderService { public String status(){ return \"old\"; } }\n", StandardCharsets.UTF_8);
        Files.writeString(docs, "# change log\ninitial\n", StandardCharsets.UTF_8);
        run(repo, "git", "add", ".");
        run(repo, "git", "commit", "-m", "initial");

        run(repo, "git", "checkout", "-b", "feature/diff-risk");
        Files.writeString(service, "package demo;\npublic class OrderService { public String status(){ return \"new\"; } }\n", StandardCharsets.UTF_8);
        Files.writeString(docs, "# change log\nupdated\n", StandardCharsets.UTF_8);
        run(repo, "git", "add", ".");
        run(repo, "git", "commit", "-m", "change service and docs");
        return repo;
    }

    private AiTestopsTestCase saveFormalCase(String testCaseId, String documentId, String requirementExtractId, String title, String riskTagsJson) {
        ensureDocumentAndExtract(documentId, requirementExtractId);
        AiTestopsTestCase testCase = new AiTestopsTestCase();
        testCase.setTestCaseId(testCaseId);
        testCase.setSourceDraftCaseId("DRAFT_" + testCaseId);
        testCase.setCaseId(testCaseId.replace("TCDB", "TC"));
        testCase.setGenerationId("GEN_" + testCaseId);
        testCase.setDocumentId(documentId);
        testCase.setRequirementExtractId(requirementExtractId);
        testCase.setTitle(title);
        testCase.setPreconditionsJson("[]");
        testCase.setStepsJson("[{\"step_no\":1,\"action\":\"Verify OrderService Service change\"}]");
        testCase.setExpectedResultsJson("[\"OrderService behavior matches expected result\"]");
        testCase.setPriority("P1");
        testCase.setCaseType("NORMAL");
        testCase.setRiskLevel("P1");
        testCase.setRequirementRefsJson("[\"REQ_DIFF_001\"]");
        testCase.setRiskTagsJson(riskTagsJson);
        testCase.setStatus("ACTIVE");
        testCase.setCreatedAt(LocalDateTime.now());
        testCase.setUpdatedAt(LocalDateTime.now());
        testCaseService.save(testCase);
        return testCase;
    }

    private void ensureDocumentAndExtract(String documentId, String requirementExtractId) {
        if (documentService.getOne(new LambdaQueryWrapper<AiTestopsDocument>()
                .eq(AiTestopsDocument::getDocumentId, documentId)
                .last("limit 1"), false) == null) {
            saveDocument(documentId, "测试需求 " + documentId);
        }
        if (requirementExtractId != null && !requirementExtractId.isBlank()
                && requirementExtractService.getOne(new LambdaQueryWrapper<AiTestopsRequirementExtract>()
                .eq(AiTestopsRequirementExtract::getRequirementExtractId, requirementExtractId)
                .last("limit 1"), false) == null) {
            saveRequirementExtract(requirementExtractId, documentId, LocalDateTime.now());
        }
    }

    private void saveDocument(String documentId, String title) {
        AiTestopsDocument document = new AiTestopsDocument();
        document.setDocumentId(documentId);
        document.setTitle(title);
        document.setSourceType("TEXT");
        document.setRawText("需求内容");
        document.setParseStatus("SUCCESS");
        document.setUploadedAt(LocalDateTime.now());
        document.setParsedAt(LocalDateTime.now());
        document.setCreatedAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());
        documentService.save(document);
    }

    private void saveRequirementExtract(String requirementExtractId, String documentId, LocalDateTime createdAt) {
        AiTestopsRequirementExtract extract = new AiTestopsRequirementExtract();
        extract.setRequirementExtractId(requirementExtractId);
        extract.setGenerationId("GEN_" + requirementExtractId);
        extract.setDocumentId(documentId);
        extract.setRequirementsJson("[]");
        extract.setBusinessRulesJson("[]");
        extract.setApiListJson("[]");
        extract.setFieldConstraintsJson("[]");
        extract.setExceptionCasesJson("[]");
        extract.setRisksJson("[]");
        extract.setRawOutputJson("{}");
        extract.setCreatedAt(createdAt);
        extract.setUpdatedAt(createdAt);
        requirementExtractService.save(extract);
    }

    private void run(Path workdir, String... command) throws Exception {
        Process process = new ProcessBuilder(List.of(command)).directory(workdir.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        assertThat(exit).as(output).isZero();
    }
}
