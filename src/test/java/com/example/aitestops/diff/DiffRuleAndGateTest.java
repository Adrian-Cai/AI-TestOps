package com.example.aitestops.diff;

import com.example.aitestops.diff.entity.AiTestopsDiffChangedFile;
import com.example.aitestops.diff.entity.AiTestopsDiffRiskItem;
import com.example.aitestops.diff.enums.DiffCoverageStatusEnum;
import com.example.aitestops.diff.enums.DiffFileRoleEnum;
import com.example.aitestops.diff.enums.DiffMergeGateStatusEnum;
import com.example.aitestops.diff.enums.DiffRiskLevelEnum;
import com.example.aitestops.diff.enums.DiffRiskProcessStatusEnum;
import com.example.aitestops.diff.gate.DiffMergeGateCalculator;
import com.example.aitestops.diff.risk.DiffFileClassifier;
import com.example.aitestops.diff.risk.DiffRuleRiskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
    void gateShouldBlockHighRiskNotCoveredAndPassCoveredRisks() {
        AiTestopsDiffRiskItem highNotCovered = risk(DiffRiskLevelEnum.HIGH.name(), DiffCoverageStatusEnum.NOT_COVERED.name(), DiffRiskProcessStatusEnum.PENDING.name());
        AiTestopsDiffRiskItem mediumNotCovered = risk(DiffRiskLevelEnum.MEDIUM.name(), DiffCoverageStatusEnum.NOT_COVERED.name(), DiffRiskProcessStatusEnum.PENDING.name());
        AiTestopsDiffRiskItem covered = risk(DiffRiskLevelEnum.HIGH.name(), DiffCoverageStatusEnum.COVERED.name(), DiffRiskProcessStatusEnum.PENDING.name());

        assertThat(gateCalculator.calculate(List.of(highNotCovered))).isEqualTo(DiffMergeGateStatusEnum.BLOCK);
        assertThat(gateCalculator.calculate(List.of(mediumNotCovered))).isEqualTo(DiffMergeGateStatusEnum.WARNING);
        assertThat(gateCalculator.calculate(List.of(covered))).isEqualTo(DiffMergeGateStatusEnum.PASS);
    }

    private AiTestopsDiffRiskItem risk(String level, String coverage, String processStatus) {
        AiTestopsDiffRiskItem risk = new AiTestopsDiffRiskItem();
        risk.setRiskLevel(level);
        risk.setCoverageStatus(coverage);
        risk.setProcessStatus(processStatus);
        return risk;
    }
}
