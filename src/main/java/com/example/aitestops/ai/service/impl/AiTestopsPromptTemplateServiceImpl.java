package com.example.aitestops.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.aitestops.ai.entity.AiTestopsPromptTemplate;
import com.example.aitestops.ai.mapper.AiTestopsPromptTemplateMapper;
import com.example.aitestops.ai.service.AiTestopsPromptTemplateService;
import com.example.aitestops.common.enums.GenerationTypeEnum;
import com.example.aitestops.common.exception.BusinessException;
import com.example.aitestops.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Prompt 模板服务实现，负责默认模板初始化和启用模板查询。
 */
@Slf4j
@Service
public class AiTestopsPromptTemplateServiceImpl
        extends ServiceImpl<AiTestopsPromptTemplateMapper, AiTestopsPromptTemplate>
        implements AiTestopsPromptTemplateService {

    @Override
    public AiTestopsPromptTemplate getEnabledTemplate(String templateCode) {
        AiTestopsPromptTemplate template = getOne(new LambdaQueryWrapper<AiTestopsPromptTemplate>()
                .eq(AiTestopsPromptTemplate::getTemplateCode, templateCode)
                .eq(AiTestopsPromptTemplate::getEnabled, 1)
                .last("limit 1"), false);
        if (template == null) {
            throw new BusinessException(ErrorCode.PROMPT_TEMPLATE_NOT_FOUND, "Prompt 模板不存在或未启用: " + templateCode);
        }
        return template;
    }

    @Override
    public void initDefaultTemplates() {
        upsertDefaultTemplate(
                "REQUIREMENT_EXTRACT",
                "需求解析默认模板",
                GenerationTypeEnum.REQUIREMENT_EXTRACT.name(),
                requirementExtractPrompt(),
                requirementExtractSchema()
        );
        upsertDefaultTemplate(
                "TEST_CASE_GENERATE",
                "测试用例生成默认模板",
                GenerationTypeEnum.TEST_CASE_GENERATE.name(),
                testCaseGeneratePrompt(),
                testCaseGenerateSchema()
        );
    }

    private void upsertDefaultTemplate(String code, String name, String type, String prompt, String schema) {
        AiTestopsPromptTemplate existing = getOne(new LambdaQueryWrapper<AiTestopsPromptTemplate>()
                .eq(AiTestopsPromptTemplate::getTemplateCode, code)
                .last("limit 1"), false);
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            AiTestopsPromptTemplate template = new AiTestopsPromptTemplate();
            template.setTemplateCode(code);
            template.setTemplateName(name);
            template.setTemplateType(type);
            template.setVersion("v1.0.0");
            template.setPromptContent(prompt);
            template.setJsonSchema(schema);
            template.setEnabled(1);
            template.setCreatedAt(now);
            template.setUpdatedAt(now);
            save(template);
            log.info("默认 Prompt 模板初始化完成: templateCode={}", code);
            return;
        }
        log.info("默认 Prompt 模板已存在: templateCode={}", code);
    }

    private String requirementExtractPrompt() {
        return """
                你是一名资深测试分析师。请从需求文档内容中提取结构化需求信息。
                要求：
                1. 只能输出 JSON，不要输出 Markdown，不要输出解释性文字。
                2. 必须包含 requirements、business_rules、api_list、field_constraints、exception_cases、risks。
                3. requirements 中每个需求点必须包含 requirement_id、title、content、priority、risk_level、source_chunks。
                4. priority 只能使用 P0、P1、P2、P3。
                5. risk_level 只能使用 HIGH、MEDIUM、LOW。
                6. source_chunks 必须引用输入中的 chunk_id。
                """;
    }

    private String testCaseGeneratePrompt() {
        return """
                你是一名资深测试设计专家。请根据结构化需求生成测试用例。
                要求：
                1. 只能输出 JSON，不要输出 Markdown，不要输出解释性文字。
                2. 每条用例必须包含 case_id、title、preconditions、steps、priority、case_type、risk_level、requirement_refs、risk_tags。
                3. steps 必须是数组，每个 step 必须包含 step_no、action、expected_result。
                4. 用例类型要覆盖正常场景、异常场景、边界场景。
                5. 每条测试用例必须关联至少一个 requirement_id。
                """;
    }

    private String requirementExtractSchema() {
        return """
                {"type":"object","required":["requirements","business_rules","api_list","field_constraints","exception_cases","risks"]}
                """;
    }

    private String testCaseGenerateSchema() {
        return """
                {"type":"object","required":["test_cases"]}
                """;
    }
}
