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
import com.example.aitestops.diff.enums.DiffRiskSourceTypeEnum;
import com.example.aitestops.common.util.IdGenerator;
import com.example.aitestops.common.util.JsonUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DiffRuleRiskService {

    private static final int RISK_TITLE_MAX_LENGTH = 255;

    private final ObjectMapper objectMapper;

    public RuleRiskDecision decide(AiTestopsDiffChangedFile file) {
        if (shouldSkipRisk(file)) {
            return null;
        }
        DiffFileRoleEnum role = DiffFileRoleEnum.valueOf(file.getFileRole());
        if (DiffChangeTypeEnum.DELETED.name().equals(file.getChangeType())) {
            return new RuleRiskDecision(DiffRiskLevelEnum.HIGH.name(), "兼容性风险", "删除文件可能影响依赖该文件的功能");
        }
        int changes = file.getChanges() == null ? 0 : file.getChanges();
        if (changes > 300) {
            return new RuleRiskDecision(DiffRiskLevelEnum.HIGH.name(), "大变更风险", "单文件变更超过 300 行，测试遗漏概率较高");
        }
        return switch (role) {
            case CONTROLLER -> new RuleRiskDecision(DiffRiskLevelEnum.MEDIUM.name(), "接口兼容", "Controller 变更可能影响接口入参、出参或权限");
            case SERVICE -> new RuleRiskDecision(DiffRiskLevelEnum.HIGH.name(), "业务逻辑", "Service 变更可能影响核心业务流程");
            case DAO -> new RuleRiskDecision(DiffRiskLevelEnum.HIGH.name(), "数据一致性", "DAO/Mapper 变更可能影响数据库读写逻辑");
            case SQL -> new RuleRiskDecision(DiffRiskLevelEnum.HIGH.name(), "数据结构", "SQL 变更可能影响表结构、数据兼容和回滚");
            case CONFIG -> new RuleRiskDecision(DiffRiskLevelEnum.MEDIUM.name(), "配置风险", "配置文件变更可能影响环境配置、开关和依赖");
            case AUTH -> new RuleRiskDecision(DiffRiskLevelEnum.HIGH.name(), "权限风险", "权限相关变更可能产生越权或权限遗漏");
            case MQ -> new RuleRiskDecision(DiffRiskLevelEnum.HIGH.name(), "异步消息", "MQ 相关变更可能产生重复消费、漏消费或幂等问题");
            case JOB -> new RuleRiskDecision(DiffRiskLevelEnum.MEDIUM.name(), "调度风险", "定时任务变更可能影响执行时间和重复执行");
            case TEST -> null;
            case OTHER -> new RuleRiskDecision(DiffRiskLevelEnum.LOW.name(), "代码变更", "普通代码变更需要基础回归确认");
        };
    }

    public List<AiTestopsDiffRiskItem> buildRuleRisks(AiTestopsDiffAnalysisTask task, List<AiTestopsDiffChangedFile> files) {
        List<AiTestopsDiffRiskItem> risks = new ArrayList<>();
        int index = 1;
        for (AiTestopsDiffChangedFile file : files) {
            RuleRiskDecision decision = decide(file);
            if (decision == null) {
                continue;
            }
            LocalDateTime now = LocalDateTime.now();
            AiTestopsDiffRiskItem risk = new AiTestopsDiffRiskItem();
            risk.setTaskId(task.getId());
            risk.setDocumentId(task.getDocumentId());
            risk.setRequirementExtractId(task.getRequirementExtractId());
            risk.setRiskCode(IdGenerator.diffRiskCode() + "_" + index++);
            risk.setRiskTitle(truncate(file.getFileRole() + " 文件变更风险: " + file.getNewFilePath(), RISK_TITLE_MAX_LENGTH));
            risk.setRiskLevel(decision.riskLevel());
            risk.setRiskCategory(decision.riskCategory());
            risk.setSourceType(DiffRiskSourceTypeEnum.RULE.name());
            risk.setSourceRuleCode("FILE_ROLE_" + file.getFileRole());
            risk.setAffectedModule(file.getNewFilePath());
            risk.setAffectedScenarios(JsonUtil.toJson(objectMapper, List.of(file.getNewFilePath())));
            risk.setRiskReason(decision.reason());
            risk.setTestSuggestion("请结合变更文件和关联需求补充回归验证，重点确认：" + decision.riskCategory());
            risk.setMissingTestScenarios(JsonUtil.toJson(objectMapper, List.of(decision.riskCategory() + "相关场景")));
            risk.setCoverageStatus(DiffCoverageStatusEnum.NEED_CONFIRM.name());
            risk.setProcessStatus(DiffRiskProcessStatusEnum.PENDING.name());
            risk.setMergeGateImpact(DiffMergeGateStatusEnum.WARNING.name());
            risk.setCreatedAt(now);
            risk.setUpdatedAt(now);
            risks.add(risk);
        }
        return risks;
    }

    public record RuleRiskDecision(String riskLevel, String riskCategory, String reason) {
    }

    private boolean shouldSkipRisk(AiTestopsDiffChangedFile file) {
        if (file == null || file.getNewFilePath() == null) {
            return true;
        }
        String path = file.getNewFilePath().replace('\\', '/').toLowerCase(Locale.ROOT);
        String fileName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        if (path.startsWith("docs/") || path.endsWith(".md") || path.endsWith(".markdown")) {
            return true;
        }
        if (path.startsWith("src/main/resources/static/assets/")
                || path.startsWith("frontend/dist/")
                || path.contains("/node_modules/")
                || path.contains("/target/")
                || path.contains("/build/")) {
            return true;
        }
        if (fileName.matches(".*\\.(png|jpg|jpeg|gif|svg|ico|webp|map|woff|woff2|ttf|eot)$")) {
            return true;
        }
        if (fileName.equals("package-lock.json") || fileName.equals("pnpm-lock.yaml") || fileName.equals("yarn.lock")) {
            return true;
        }
        return "git".equals(fileName);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
