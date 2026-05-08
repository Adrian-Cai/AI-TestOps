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
        Path repo = createLocalRepositoryWithFeatureDiff();
        saveFormalCase();

        DiffAnalysisTaskCreateRequest request = new DiffAnalysisTaskCreateRequest();
        request.setDocumentId("DOC_DIFF_001");
        request.setRequirementExtractId("REXT_DIFF_001");
        request.setRepoUrl(repo.toString());
        request.setSourceBranch("feature/diff-risk");
        request.setTargetBranch("master");
        DiffAnalysisOptions options = new DiffAnalysisOptions();
        options.setEnableCoverageCheck(true);
        options.setEnableAiAnalysis(false);
        request.setAnalysisOptions(options);

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

    private Path createLocalRepositoryWithFeatureDiff() throws Exception {
        Path repo = tempDir.resolve("repo");
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

    private void saveFormalCase() {
        AiTestopsTestCase testCase = new AiTestopsTestCase();
        testCase.setTestCaseId("TCDB_DIFF_001");
        testCase.setSourceDraftCaseId("DRAFT_DIFF_001");
        testCase.setCaseId("TC_DIFF_001");
        testCase.setGenerationId("GEN_DIFF_001");
        testCase.setDocumentId("DOC_DIFF_001");
        testCase.setRequirementExtractId("REXT_DIFF_001");
        testCase.setTitle("OrderService 业务逻辑变更回归");
        testCase.setPreconditionsJson("[]");
        testCase.setStepsJson("[{\"step_no\":1,\"action\":\"验证 OrderService 业务逻辑\"}]");
        testCase.setExpectedResultsJson("[\"业务逻辑符合预期\"]");
        testCase.setPriority("P1");
        testCase.setCaseType("NORMAL");
        testCase.setRiskLevel("P1");
        testCase.setRequirementRefsJson("[\"REQ_DIFF_001\"]");
        testCase.setRiskTagsJson("[\"业务逻辑\",\"Service\"]");
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
