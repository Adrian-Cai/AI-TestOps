package com.example.aitestops.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.aitestops.ai.dto.PromptTemplateVersionCreateRequest;
import com.example.aitestops.ai.entity.AiTestopsGenerationRecord;
import com.example.aitestops.ai.entity.AiTestopsPromptTemplate;
import com.example.aitestops.ai.entity.AiTestopsValidationResult;
import com.example.aitestops.ai.mapper.AiTestopsGenerationRecordMapper;
import com.example.aitestops.ai.mapper.AiTestopsPromptTemplateMapper;
import com.example.aitestops.ai.mapper.AiTestopsValidationResultMapper;
import com.example.aitestops.ai.service.AiTestopsPromptTemplateService;
import com.example.aitestops.ai.vo.PromptQualityVO;
import com.example.aitestops.ai.vo.PromptTemplateVO;
import com.example.aitestops.common.enums.GenerationTypeEnum;
import com.example.aitestops.common.enums.GenerationStatusEnum;
import com.example.aitestops.common.enums.ReviewActionEnum;
import com.example.aitestops.common.enums.ValidationStatusEnum;
import com.example.aitestops.common.exception.BusinessException;
import com.example.aitestops.common.exception.ErrorCode;
import com.example.aitestops.review.entity.AiTestopsReviewRecord;
import com.example.aitestops.review.mapper.AiTestopsReviewRecordMapper;
import com.example.aitestops.testcase.entity.AiTestopsTestCaseDraft;
import com.example.aitestops.testcase.mapper.AiTestopsTestCaseDraftMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Prompt 模板服务实现，负责默认模板初始化和启用模板查询。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiTestopsPromptTemplateServiceImpl
        extends ServiceImpl<AiTestopsPromptTemplateMapper, AiTestopsPromptTemplate>
        implements AiTestopsPromptTemplateService {

    private final AiTestopsGenerationRecordMapper generationRecordMapper;
    private final AiTestopsValidationResultMapper validationResultMapper;
    private final AiTestopsTestCaseDraftMapper testCaseDraftMapper;
    private final AiTestopsReviewRecordMapper reviewRecordMapper;

    @Override
    public AiTestopsPromptTemplate getEnabledTemplate(String templateCode) {
        AiTestopsPromptTemplate template = getOne(new LambdaQueryWrapper<AiTestopsPromptTemplate>()
                .eq(AiTestopsPromptTemplate::getTemplateCode, templateCode)
                .eq(AiTestopsPromptTemplate::getEnabled, 1)
                .orderByDesc(AiTestopsPromptTemplate::getUpdatedAt)
                .last("limit 1"), false);
        if (template == null) {
            throw new BusinessException(ErrorCode.PROMPT_TEMPLATE_NOT_FOUND, "Prompt 模板不存在或未启用: " + templateCode);
        }
        return template;
    }

    @Override
    public List<PromptTemplateVO> listVersions(String templateCode, String templateType) {
        LambdaQueryWrapper<AiTestopsPromptTemplate> wrapper = new LambdaQueryWrapper<AiTestopsPromptTemplate>()
                .orderByAsc(AiTestopsPromptTemplate::getTemplateCode)
                .orderByDesc(AiTestopsPromptTemplate::getUpdatedAt);
        if (StringUtils.hasText(templateCode)) {
            wrapper.eq(AiTestopsPromptTemplate::getTemplateCode, templateCode);
        }
        if (StringUtils.hasText(templateType)) {
            wrapper.eq(AiTestopsPromptTemplate::getTemplateType, templateType);
        }
        return list(wrapper).stream().map(this::toVO).toList();
    }

    @Override
    @Transactional
    public PromptTemplateVO createVersion(PromptTemplateVersionCreateRequest request) {
        validateCreateRequest(request);
        AiTestopsPromptTemplate existing = getOne(new LambdaQueryWrapper<AiTestopsPromptTemplate>()
                .eq(AiTestopsPromptTemplate::getTemplateCode, request.getTemplateCode())
                .eq(AiTestopsPromptTemplate::getVersion, request.getVersion())
                .last("limit 1"), false);
        if (existing != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Prompt 模板版本已存在: " + request.getTemplateCode() + " " + request.getVersion());
        }

        LocalDateTime now = LocalDateTime.now();
        if (Boolean.TRUE.equals(request.getEnabled())) {
            disableOtherVersions(request.getTemplateCode(), now);
        }

        AiTestopsPromptTemplate template = new AiTestopsPromptTemplate();
        template.setTemplateCode(request.getTemplateCode());
        template.setTemplateName(request.getTemplateName());
        template.setTemplateType(request.getTemplateType());
        template.setVersion(request.getVersion());
        template.setPromptContent(request.getPromptContent());
        template.setJsonSchema(request.getJsonSchema());
        template.setEnabled(Boolean.TRUE.equals(request.getEnabled()) ? 1 : 0);
        template.setCreatedAt(now);
        template.setUpdatedAt(now);
        save(template);
        log.info("Prompt 模板版本创建: templateCode={}, version={}, enabled={}",
                template.getTemplateCode(), template.getVersion(), template.getEnabled());
        return toVO(template);
    }

    @Override
    @Transactional
    public PromptTemplateVO activateVersion(String templateCode, String version) {
        AiTestopsPromptTemplate template = getOne(new LambdaQueryWrapper<AiTestopsPromptTemplate>()
                .eq(AiTestopsPromptTemplate::getTemplateCode, templateCode)
                .eq(AiTestopsPromptTemplate::getVersion, version)
                .last("limit 1"), false);
        if (template == null) {
            throw new BusinessException(ErrorCode.PROMPT_TEMPLATE_NOT_FOUND,
                    "Prompt 模板版本不存在: " + templateCode + " " + version);
        }

        LocalDateTime now = LocalDateTime.now();
        disableOtherVersions(templateCode, now);
        template.setEnabled(1);
        template.setUpdatedAt(now);
        updateById(template);
        log.info("Prompt 模板版本启用: templateCode={}, version={}", templateCode, version);
        return toVO(template);
    }

    @Override
    public List<PromptQualityVO> listQuality(String templateCode, String generationType) {
        return listVersions(templateCode, null).stream()
                .map(template -> buildQuality(template, generationType))
                .toList();
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
                .eq(AiTestopsPromptTemplate::getVersion, "v1.0.0")
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

    private void validateCreateRequest(PromptTemplateVersionCreateRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Prompt 模板内容不能为空");
        }
    }

    private void disableOtherVersions(String templateCode, LocalDateTime now) {
        update(new LambdaUpdateWrapper<AiTestopsPromptTemplate>()
                .eq(AiTestopsPromptTemplate::getTemplateCode, templateCode)
                .set(AiTestopsPromptTemplate::getEnabled, 0)
                .set(AiTestopsPromptTemplate::getUpdatedAt, now));
    }

    private PromptTemplateVO toVO(AiTestopsPromptTemplate template) {
        PromptTemplateVO vo = new PromptTemplateVO();
        vo.setId(template.getId());
        vo.setTemplateCode(template.getTemplateCode());
        vo.setTemplateName(template.getTemplateName());
        vo.setTemplateType(template.getTemplateType());
        vo.setVersion(template.getVersion());
        vo.setPromptContent(template.getPromptContent());
        vo.setJsonSchema(template.getJsonSchema());
        vo.setEnabled(template.getEnabled());
        vo.setCreatedAt(template.getCreatedAt());
        vo.setUpdatedAt(template.getUpdatedAt());
        return vo;
    }

    private PromptQualityVO buildQuality(PromptTemplateVO template, String generationType) {
        LambdaQueryWrapper<AiTestopsGenerationRecord> wrapper = new LambdaQueryWrapper<AiTestopsGenerationRecord>()
                .eq(AiTestopsGenerationRecord::getPromptTemplateCode, template.getTemplateCode())
                .eq(AiTestopsGenerationRecord::getPromptTemplateVersion, template.getVersion());
        if (StringUtils.hasText(generationType)) {
            wrapper.eq(AiTestopsGenerationRecord::getGenerationType, generationType);
        }
        List<AiTestopsGenerationRecord> records = generationRecordMapper.selectList(wrapper);
        Set<String> generationIds = new HashSet<>(records.stream().map(AiTestopsGenerationRecord::getGenerationId).toList());
        Set<String> validationFailedGenerationIds = listValidationFailedGenerationIds(generationIds);
        List<AiTestopsTestCaseDraft> drafts = listDrafts(generationIds);
        Set<String> draftCaseIds = new HashSet<>(drafts.stream().map(AiTestopsTestCaseDraft::getDraftCaseId).toList());
        List<AiTestopsReviewRecord> reviews = listReviews(draftCaseIds);

        long successCount = records.stream().filter(item -> GenerationStatusEnum.SUCCESS.name().equals(item.getStatus())).count();
        long failedCount = records.stream().filter(item -> GenerationStatusEnum.FAILED.name().equals(item.getStatus())).count();
        long approvedCount = reviews.stream().filter(item -> ReviewActionEnum.APPROVE.name().equals(item.getAction())).count();
        long rejectedCount = reviews.stream().filter(item -> ReviewActionEnum.REJECT.name().equals(item.getAction())).count();
        long editedCount = reviews.stream().filter(item -> ReviewActionEnum.EDIT.name().equals(item.getAction())).count();

        PromptQualityVO vo = new PromptQualityVO();
        vo.setTemplateCode(template.getTemplateCode());
        vo.setVersion(template.getVersion());
        vo.setTemplateName(template.getTemplateName());
        vo.setTemplateType(template.getTemplateType());
        vo.setEnabled(template.getEnabled());
        vo.setGenerationType(generationType);
        vo.setTotalGenerations((long) records.size());
        vo.setSuccessGenerations(successCount);
        vo.setFailedGenerations(failedCount);
        vo.setValidationFailedGenerations((long) validationFailedGenerationIds.size());
        vo.setDraftCount((long) drafts.size());
        vo.setApprovedCount(approvedCount);
        vo.setRejectedCount(rejectedCount);
        vo.setEditedCount(editedCount);
        vo.setSuccessRate(rate(successCount, records.size()));
        vo.setValidationPassRate(rate(records.size() - validationFailedGenerationIds.size(), records.size()));
        vo.setApproveRate(rate(approvedCount, drafts.size()));
        vo.setAvgTokenInput(avg(records.stream().map(AiTestopsGenerationRecord::getTokenInput).toList()));
        vo.setAvgTokenOutput(avg(records.stream().map(AiTestopsGenerationRecord::getTokenOutput).toList()));
        return vo;
    }

    private Set<String> listValidationFailedGenerationIds(Set<String> generationIds) {
        if (generationIds.isEmpty()) {
            return Set.of();
        }
        List<AiTestopsValidationResult> failed = validationResultMapper.selectList(new LambdaQueryWrapper<AiTestopsValidationResult>()
                .in(AiTestopsValidationResult::getGenerationId, generationIds)
                .eq(AiTestopsValidationResult::getStatus, ValidationStatusEnum.FAILED.name()));
        return new HashSet<>(failed.stream().map(AiTestopsValidationResult::getGenerationId).toList());
    }

    private List<AiTestopsTestCaseDraft> listDrafts(Set<String> generationIds) {
        if (generationIds.isEmpty()) {
            return List.of();
        }
        return testCaseDraftMapper.selectList(new LambdaQueryWrapper<AiTestopsTestCaseDraft>()
                .in(AiTestopsTestCaseDraft::getGenerationId, generationIds));
    }

    private List<AiTestopsReviewRecord> listReviews(Set<String> draftCaseIds) {
        if (draftCaseIds.isEmpty()) {
            return List.of();
        }
        return reviewRecordMapper.selectList(new LambdaQueryWrapper<AiTestopsReviewRecord>()
                .in(AiTestopsReviewRecord::getDraftCaseId, draftCaseIds));
    }

    private Double rate(long numerator, long denominator) {
        if (denominator == 0) {
            return 0.0;
        }
        return Math.round(numerator * 10000.0 / denominator) / 100.0;
    }

    private Double avg(List<Integer> values) {
        return values.stream()
                .filter(value -> value != null)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);
    }

    private String requirementExtractPrompt() {
        return """
                你是一名资深测试分析师。请从需求文档内容中提取结构化需求信息。
                要求：
                1. 只能输出 JSON，不要输出 Markdown，不要输出解释性文字。
                2. 必须包含 requirements、business_rules、api_list、field_constraints、exception_cases、risks。
                3. requirements 中每个需求点必须包含 requirement_id、title、content、priority、risk_level、source_chunks。
                4. priority 只能使用 P0、P1、P2、P3。
                5. risk_level 只能使用 P0、P1、P2（分别对应高、中、低）。
                6. source_chunks 必须引用输入中的 chunk_id。
                """;
    }

    private String testCaseGeneratePrompt() {
        return """
                你是一名资深测试设计专家。请根据结构化需求生成测试用例。
                要求：
                1. 只能输出 JSON，不要输出 Markdown，不要输出解释性文字。
                2. 每条用例必须包含 case_id、title、preconditions、steps、expected_results、priority、case_type、risk_level、requirement_refs、risk_tags。
                3. steps 必须是数组，每个 step 必须包含 step_no、action；expected_results 必须是与 steps 一一对应的字符串数组。
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
                {
                  "type":"object",
                  "required":["test_cases"],
                  "properties":{
                    "test_cases":{
                      "type":"array",
                      "items":{
                        "type":"object",
                        "required":["case_id","title","preconditions","steps","expected_results","priority","case_type","risk_level","requirement_refs","risk_tags"],
                        "properties":{
                          "case_id":{"type":"string"},
                          "title":{"type":"string"},
                          "preconditions":{"type":"array"},
                          "steps":{
                            "type":"array",
                            "items":{
                              "type":"object",
                              "required":["step_no","action"],
                              "properties":{
                                "step_no":{"type":"integer"},
                                "action":{"type":"string"}
                              }
                            }
                          },
                          "expected_results":{"type":"array","items":{"type":"string"}},
                          "priority":{"type":"string"},
                          "case_type":{"type":"string"},
                          "risk_level":{"type":"string"},
                          "requirement_refs":{"type":"array"},
                          "risk_tags":{"type":"array"}
                        }
                      }
                    }
                  }
                }
                """;
    }
}
