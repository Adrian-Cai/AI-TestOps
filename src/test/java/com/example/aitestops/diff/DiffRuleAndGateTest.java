package com.example.aitestops.diff;

import com.example.aitestops.common.exception.BusinessException;
import com.example.aitestops.diff.entity.AiTestopsDiffAnalysisTask;
import com.example.aitestops.diff.entity.AiTestopsDiffChangedFile;
import com.example.aitestops.diff.entity.AiTestopsDiffRiskItem;
import com.example.aitestops.diff.enums.DiffCoverageStatusEnum;
import com.example.aitestops.diff.enums.DiffFileRoleEnum;
import com.example.aitestops.diff.enums.DiffMergeGateStatusEnum;
import com.example.aitestops.diff.enums.DiffRiskLevelEnum;
import com.example.aitestops.diff.enums.DiffRiskProcessStatusEnum;
import com.example.aitestops.diff.gate.DiffMergeGateCalculator;
import com.example.aitestops.diff.git.GitDiffClient;
import com.example.aitestops.diff.risk.DiffFileClassifier;
import com.example.aitestops.diff.risk.DiffRuleRiskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiffRuleAndGateTest {

    private final DiffFileClassifier classifier = new DiffFileClassifier();
    private final DiffRuleRiskService riskService = new DiffRuleRiskService(new ObjectMapper());
    private final DiffMergeGateCalculator gateCalculator = new DiffMergeGateCalculator();

    @Test
    void classifyShouldDetectCommonSpringFileRoles() {
        assertThat(classifier.classify("src/main/java/demo/UserController.java")).isEqualTo(DiffFileRoleEnum.CONTROLLER);
        assertThat(classifier.classify("src/main/java/demo/OrderService.java")).isEqualTo(DiffFileRoleEnum.SERVICE);
        assertThat(classifier.classify("src/main/java/demo/UserMapper.java")).isEqualTo(DiffFileRoleEnum.DAO);
        assertThat(classifier.classify("docs/sql/schema.sql")).isEqualTo(DiffFileRoleEnum.SQL);
        assertThat(classifier.classify("src/test/java/demo/UserServiceTest.java")).isEqualTo(DiffFileRoleEnum.TEST);
    }

    @Test
    void ruleRiskShouldMarkServiceChangesAsHighBusinessRisk() {
        AiTestopsDiffChangedFile file = new AiTestopsDiffChangedFile();
        file.setNewFilePath("src/main/java/demo/OrderService.java");
        file.setChangeType("MODIFIED");
        file.setFileRole(DiffFileRoleEnum.SERVICE.name());
        file.setChanges(12);

        DiffRuleRiskService.RuleRiskDecision decision = riskService.decide(file);

        assertThat(decision.riskLevel()).isEqualTo(DiffRiskLevelEnum.HIGH.name());
        assertThat(decision.riskCategory()).isEqualTo("业务逻辑");
    }

    @Test
    void ruleRiskShouldIgnoreDocsAndGeneratedStaticAssets() {
        assertThat(riskService.decide(file("docs/Diff/module.md", DiffFileRoleEnum.OTHER.name(), "MARKDOWN"))).isNull();
        assertThat(riskService.decide(file("src/main/resources/static/assets/index-abc123.js", DiffFileRoleEnum.OTHER.name(), "JAVASCRIPT"))).isNull();
        assertThat(riskService.decide(file("src/main/java/demo/OrderService.java", DiffFileRoleEnum.SERVICE.name(), "JAVA"))).isNotNull();
    }

    @Test
    void buildRuleRisksShouldFitRiskTitleDatabaseLimitForLongPaths() {
        String longPath = "src/main/java/demo/" + "VeryLongPackageName".repeat(20) + "/OrderService.java";
        AiTestopsDiffAnalysisTask task = new AiTestopsDiffAnalysisTask();
        task.setId(1L);
        AiTestopsDiffChangedFile file = file(longPath, DiffFileRoleEnum.SERVICE.name(), "JAVA");

        List<AiTestopsDiffRiskItem> risks = riskService.buildRuleRisks(task, List.of(file));

        assertThat(risks).hasSize(1);
        assertThat(risks.get(0).getRiskTitle()).hasSizeLessThanOrEqualTo(255);
        assertThat(risks.get(0).getAffectedScenarios()).contains(longPath);
    }

    @Test
    void gateShouldBlockHighRiskNotCoveredAndPassCoveredRisks() {
        AiTestopsDiffRiskItem highNotCovered = risk(DiffRiskLevelEnum.HIGH.name(), DiffCoverageStatusEnum.NOT_COVERED.name(), DiffRiskProcessStatusEnum.PENDING.name());
        AiTestopsDiffRiskItem mediumNotCovered = risk(DiffRiskLevelEnum.MEDIUM.name(), DiffCoverageStatusEnum.NOT_COVERED.name(), DiffRiskProcessStatusEnum.PENDING.name());
        AiTestopsDiffRiskItem covered = risk(DiffRiskLevelEnum.HIGH.name(), DiffCoverageStatusEnum.COVERED.name(), DiffRiskProcessStatusEnum.PENDING.name());

        assertThat(gateCalculator.calculate(List.of(highNotCovered))).isEqualTo(DiffMergeGateStatusEnum.BLOCK);
        assertThat(gateCalculator.calculate(List.of(mediumNotCovered))).isEqualTo(DiffMergeGateStatusEnum.WARNING);
        assertThat(gateCalculator.calculate(List.of(covered))).isEqualTo(DiffMergeGateStatusEnum.PASS);
    }

    @Test
    void gitClientShouldRejectUnsafeRepositoryUrlsByDefault() {
        GitDiffClient client = new GitDiffClient();

        assertThatThrownBy(() -> client.diff("http://github.com/acai1998/AI-TestOps.git", "feature", "master"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("HTTPS");
        assertThatThrownBy(() -> client.diff("https://example.com/repo.git", "feature", "master"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("白名单");
        assertThatThrownBy(() -> client.diff("C:/repo", "feature", "master"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("本地仓库路径未启用");
    }

    @Test
    void gitClientShouldUseCachedBranchesWhenRemoteLookupFails() {
        GitDiffClient client = new GitDiffClient();
        ReflectionTestUtils.setField(client, "allowedHosts", "localhost");
        String repoUrl = "https://localhost/acai1998/AI-TestOps.git";
        @SuppressWarnings("unchecked")
        Map<String, List<String>> branchCache = (Map<String, List<String>>) ReflectionTestUtils.getField(client, "branchCache");
        branchCache.put(repoUrl, List.of("master", "feature/diff-analysis-task"));

        List<String> branches = client.listBranches(repoUrl);

        assertThat(branches).containsExactly("master", "feature/diff-analysis-task");
    }

    @Test
    void gitClientShouldReturnFriendlyMessageWhenRemoteLookupFailsWithoutCache() {
        GitDiffClient client = new GitDiffClient();
        ReflectionTestUtils.setField(client, "allowedHosts", "localhost");

        assertThatThrownBy(() -> client.listBranches("https://localhost/acai1998/AI-TestOps.git"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法连接 Git 远程仓库");
    }

    private AiTestopsDiffRiskItem risk(String level, String coverage, String processStatus) {
        AiTestopsDiffRiskItem risk = new AiTestopsDiffRiskItem();
        risk.setRiskLevel(level);
        risk.setCoverageStatus(coverage);
        risk.setProcessStatus(processStatus);
        return risk;
    }

    private AiTestopsDiffChangedFile file(String path, String role, String language) {
        AiTestopsDiffChangedFile file = new AiTestopsDiffChangedFile();
        file.setNewFilePath(path);
        file.setChangeType("MODIFIED");
        file.setFileRole(role);
        file.setLanguage(language);
        file.setChanges(8);
        return file;
    }
}
