package com.example.aitestops.diff.risk;

import com.example.aitestops.diff.entity.AiTestopsDiffAnalysisTask;
import com.example.aitestops.diff.entity.AiTestopsDiffChangedFile;
import com.example.aitestops.diff.entity.AiTestopsDiffRiskItem;
import com.example.aitestops.diff.enums.DiffChangeTypeEnum;
import com.example.aitestops.diff.enums.DiffCoverageStatusEnum;
import com.example.aitestops.diff.enums.DiffFileRoleEnum;
import com.example.aitestops.diff.enums.DiffMergeGateStatusEnum;
import com.example.aitestops.diff.enums.DiffRiskLevelEnum;
import com.example.aitestops.diff.enums.DiffRiskProcessStatusEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiffRuleRiskServiceTest {

    private final DiffRuleRiskService service = new DiffRuleRiskService(new ObjectMapper());

    @Test
    void decideShouldSkipDocumentationAssetsAndLockFiles() {
        assertThat(service.decide(file("docs/readme.md", DiffFileRoleEnum.OTHER, DiffChangeTypeEnum.MODIFIED, 3))).isNull();
        assertThat(service.decide(file("frontend/dist/assets/index.js", DiffFileRoleEnum.OTHER, DiffChangeTypeEnum.MODIFIED, 20))).isNull();
        assertThat(service.decide(file("src/main/resources/static/assets/logo.svg", DiffFileRoleEnum.OTHER, DiffChangeTypeEnum.MODIFIED, 1))).isNull();
        assertThat(service.decide(file("frontend/package-lock.json", DiffFileRoleEnum.OTHER, DiffChangeTypeEnum.MODIFIED, 80))).isNull();
    }

    @Test
    void decideShouldUseDeleteAndLargeChangeRulesBeforeRoleRules() {
        DiffRuleRiskService.RuleRiskDecision deleted = service.decide(
                file("src/main/java/com/example/DemoController.java", DiffFileRoleEnum.CONTROLLER, DiffChangeTypeEnum.DELETED, 10)
        );
        DiffRuleRiskService.RuleRiskDecision large = service.decide(
                file("src/main/java/com/example/DemoController.java", DiffFileRoleEnum.CONTROLLER, DiffChangeTypeEnum.MODIFIED, 301)
        );

        assertThat(deleted.riskLevel()).isEqualTo(DiffRiskLevelEnum.HIGH.name());
        assertThat(deleted.riskCategory()).isEqualTo("兼容性风险");
        assertThat(large.riskLevel()).isEqualTo(DiffRiskLevelEnum.HIGH.name());
        assertThat(large.riskCategory()).isEqualTo("大变更风险");
    }

    @Test
    void decideShouldMapFileRoleToBusinessRisk() {
        assertThat(service.decide(file("OrderController.java", DiffFileRoleEnum.CONTROLLER, DiffChangeTypeEnum.MODIFIED, 10)))
                .extracting(DiffRuleRiskService.RuleRiskDecision::riskLevel, DiffRuleRiskService.RuleRiskDecision::riskCategory)
                .containsExactly(DiffRiskLevelEnum.MEDIUM.name(), "接口兼容");
        assertThat(service.decide(file("OrderServiceImpl.java", DiffFileRoleEnum.SERVICE, DiffChangeTypeEnum.MODIFIED, 10)))
                .extracting(DiffRuleRiskService.RuleRiskDecision::riskLevel, DiffRuleRiskService.RuleRiskDecision::riskCategory)
                .containsExactly(DiffRiskLevelEnum.HIGH.name(), "业务逻辑");
        assertThat(service.decide(file("OrderMapper.java", DiffFileRoleEnum.DAO, DiffChangeTypeEnum.MODIFIED, 10)))
                .extracting(DiffRuleRiskService.RuleRiskDecision::riskLevel, DiffRuleRiskService.RuleRiskDecision::riskCategory)
                .containsExactly(DiffRiskLevelEnum.HIGH.name(), "数据一致性");
        assertThat(service.decide(file("application.yml", DiffFileRoleEnum.CONFIG, DiffChangeTypeEnum.MODIFIED, 10)))
                .extracting(DiffRuleRiskService.RuleRiskDecision::riskLevel, DiffRuleRiskService.RuleRiskDecision::riskCategory)
                .containsExactly(DiffRiskLevelEnum.MEDIUM.name(), "配置风险");
        assertThat(service.decide(file("README.txt", DiffFileRoleEnum.OTHER, DiffChangeTypeEnum.MODIFIED, 10)))
                .extracting(DiffRuleRiskService.RuleRiskDecision::riskLevel, DiffRuleRiskService.RuleRiskDecision::riskCategory)
                .containsExactly(DiffRiskLevelEnum.LOW.name(), "代码变更");
    }

    @Test
    void buildRuleRisksShouldPopulateRiskMetadataAndSkipNonCodeFiles() {
        AiTestopsDiffAnalysisTask task = new AiTestopsDiffAnalysisTask();
        task.setId(12L);
        task.setDocumentId("DOC_1");
        task.setRequirementExtractId("REQ_1");
        AiTestopsDiffChangedFile serviceFile = file(
                "src/main/java/com/example/aitestops/order/service/" + "VeryLongServiceName".repeat(20) + ".java",
                DiffFileRoleEnum.SERVICE,
                DiffChangeTypeEnum.MODIFIED,
                20
        );
        AiTestopsDiffChangedFile docFile = file("docs/change.md", DiffFileRoleEnum.OTHER, DiffChangeTypeEnum.MODIFIED, 2);

        List<AiTestopsDiffRiskItem> risks = service.buildRuleRisks(task, List.of(serviceFile, docFile));

        assertThat(risks).hasSize(1);
        AiTestopsDiffRiskItem risk = risks.get(0);
        assertThat(risk.getTaskId()).isEqualTo(12L);
        assertThat(risk.getDocumentId()).isEqualTo("DOC_1");
        assertThat(risk.getRequirementExtractId()).isEqualTo("REQ_1");
        assertThat(risk.getRiskTitle()).hasSizeLessThanOrEqualTo(255);
        assertThat(risk.getRiskLevel()).isEqualTo(DiffRiskLevelEnum.HIGH.name());
        assertThat(risk.getCoverageStatus()).isEqualTo(DiffCoverageStatusEnum.NEED_CONFIRM.name());
        assertThat(risk.getProcessStatus()).isEqualTo(DiffRiskProcessStatusEnum.PENDING.name());
        assertThat(risk.getMergeGateImpact()).isEqualTo(DiffMergeGateStatusEnum.WARNING.name());
        assertThat(risk.getSourceRuleCode()).isEqualTo("FILE_ROLE_SERVICE");
        assertThat(risk.getAffectedScenarios()).contains(serviceFile.getNewFilePath());
        assertThat(risk.getMissingTestScenarios()).contains("业务逻辑相关场景");
    }

    private AiTestopsDiffChangedFile file(String path, DiffFileRoleEnum role, DiffChangeTypeEnum changeType, int changes) {
        AiTestopsDiffChangedFile file = new AiTestopsDiffChangedFile();
        file.setNewFilePath(path);
        file.setFileRole(role.name());
        file.setChangeType(changeType.name());
        file.setChanges(changes);
        return file;
    }
}
