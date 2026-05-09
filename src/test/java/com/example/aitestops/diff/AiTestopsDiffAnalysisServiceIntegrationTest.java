package com.example.aitestops.diff;

import com.example.aitestops.diff.dto.DiffAnalysisOptions;
import com.example.aitestops.diff.dto.DiffAnalysisTaskCreateRequest;
import com.example.aitestops.diff.service.AiTestopsDiffAnalysisTaskService;
import com.example.aitestops.diff.vo.DiffAnalysisReportVO;
import com.example.aitestops.diff.vo.DiffAnalysisTaskVO;
import com.example.aitestops.testcase.entity.AiTestopsTestCase;
import com.example.aitestops.testcase.service.AiTestopsTestCaseService;
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

@SpringBootTest
@ActiveProfiles("test")
class AiTestopsDiffAnalysisServiceIntegrationTest {

    @Autowired
    private AiTestopsDiffAnalysisTaskService diffAnalysisTaskService;

    @Autowired
    private AiTestopsTestCaseService testCaseService;

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
        assertThat(report.getRiskList()).hasSize(1);
        assertThat(report.getRiskList().get(0).getRiskLevel()).isEqualTo("HIGH");
        assertThat(report.getReport()).isNotNull();
        assertThat(report.getReport().getGateStatus()).isIn("PASS", "WARNING", "BLOCK", "MANUAL_REVIEW");
    }

    @Test
    void createAndAnalyzeShouldMatchCasesByDocumentWhenRequirementIdDiffers() throws Exception {
        Path repo = createLocalRepositoryWithFeatureDiff("repo-document-fallback");
        saveFormalCase("TCDB_DIFF_DOC_ONLY", "DOC_DIFF_DOC_ONLY", "REXT_OLD",
                "OrderService Service file change risk regression", "[\"Service\",\"OrderService\",\"file change\"]");

        DiffAnalysisReportVO report = diffAnalysisTaskService.getReport(
                diffAnalysisTaskService.createAndAnalyze(baseRequest(repo, "DOC_DIFF_DOC_ONLY", "REXT_NEW")).getTaskId());

        assertThat(report.getRiskList()).hasSize(1);
        assertThat(report.getRiskList().get(0).getMatchedCases()).isNotEmpty();
        assertThat(report.getRiskList().get(0).getCoverageStatus()).isIn("COVERED", "PARTIAL_COVERED");
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

    private void saveFormalCase(String testCaseId, String documentId, String requirementExtractId, String title, String riskTagsJson) {
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
    }

    private void run(Path workdir, String... command) throws Exception {
        Process process = new ProcessBuilder(List.of(command)).directory(workdir.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        assertThat(exit).as(output).isZero();
    }
}
