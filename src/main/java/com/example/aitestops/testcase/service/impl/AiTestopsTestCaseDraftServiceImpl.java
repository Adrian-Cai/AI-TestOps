package com.example.aitestops.testcase.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.aitestops.ai.client.AiClient;
import com.example.aitestops.ai.dto.AiChatRequest;
import com.example.aitestops.ai.dto.AiChatResponse;
import com.example.aitestops.ai.entity.AiTestopsGenerationRecord;
import com.example.aitestops.ai.entity.AiTestopsPromptTemplate;
import com.example.aitestops.ai.entity.AiTestopsRequirementExtract;
import com.example.aitestops.ai.entity.AiTestopsValidationResult;
import com.example.aitestops.ai.service.AiTestopsGenerationRecordService;
import com.example.aitestops.ai.service.AiTestopsPromptTemplateService;
import com.example.aitestops.ai.service.AiTestopsRequirementExtractService;
import com.example.aitestops.ai.service.AiTestopsValidationResultService;
import com.example.aitestops.common.config.AiModelProperties;
import com.example.aitestops.common.enums.GenerationStatusEnum;
import com.example.aitestops.common.enums.GenerationTypeEnum;
import com.example.aitestops.common.enums.ParseStatusEnum;
import com.example.aitestops.common.enums.ReviewActionEnum;
import com.example.aitestops.common.enums.ReviewStatusEnum;
import com.example.aitestops.common.enums.ValidationStatusEnum;
import com.example.aitestops.common.enums.ValidationTypeEnum;
import com.example.aitestops.common.exception.BusinessException;
import com.example.aitestops.common.exception.ErrorCode;
import com.example.aitestops.common.util.IdGenerator;
import com.example.aitestops.common.util.JsonUtil;
import com.example.aitestops.document.entity.AiTestopsDocument;
import com.example.aitestops.document.entity.AiTestopsDocumentChunk;
import com.example.aitestops.document.service.AiTestopsDocumentChunkService;
import com.example.aitestops.document.service.AiTestopsDocumentService;
import com.example.aitestops.review.service.AiTestopsReviewRecordService;
import com.example.aitestops.testcase.dto.TestCaseDraftBatchApproveRequest;
import com.example.aitestops.testcase.dto.TestCaseDraftReviewRequest;
import com.example.aitestops.testcase.dto.TestCaseDraftUpdateRequest;
import com.example.aitestops.testcase.dto.TestCaseGenerateRequest;
import com.example.aitestops.testcase.entity.AiTestopsRequirementCaseMapping;
import com.example.aitestops.testcase.entity.AiTestopsTestCase;
import com.example.aitestops.testcase.entity.AiTestopsTestCaseDraft;
import com.example.aitestops.testcase.mapper.AiTestopsTestCaseDraftMapper;
import com.example.aitestops.testcase.service.AiTestopsRequirementCaseMappingService;
import com.example.aitestops.testcase.service.AiTestopsTestCaseDraftService;
import com.example.aitestops.testcase.service.AiTestopsTestCaseService;
import com.example.aitestops.testcase.vo.TestCaseDraftVO;
import com.example.aitestops.testcase.vo.TestCaseGenerateVO;
import com.example.aitestops.testcase.vo.TestCaseVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 测试用例草稿服务实现，负责生成、校验并保存 AI 测试用例草稿。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiTestopsTestCaseDraftServiceImpl
        extends ServiceImpl<AiTestopsTestCaseDraftMapper, AiTestopsTestCaseDraft>
        implements AiTestopsTestCaseDraftService {

    private final AiTestopsDocumentService documentService;
    private final AiTestopsDocumentChunkService documentChunkService;
    private final AiTestopsRequirementExtractService requirementExtractService;
    private final AiTestopsPromptTemplateService promptTemplateService;
    private final AiTestopsGenerationRecordService generationRecordService;
    private final AiTestopsValidationResultService validationResultService;
    private final AiTestopsTestCaseService testCaseService;
    private final AiTestopsRequirementCaseMappingService requirementCaseMappingService;
    private final AiTestopsReviewRecordService reviewRecordService;
    private final AiClient aiClient;
    private final AiModelProperties aiModelProperties;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public TestCaseGenerateVO generateDrafts(TestCaseGenerateRequest request) {
        validateGenerateRequest(request);
        AiTestopsRequirementExtract requirementExtract = resolveRequirementExtract(request);
        AiTestopsDocument document = resolveParsedDocument(request, requirementExtract);
        AiTestopsPromptTemplate template = promptTemplateService.getEnabledTemplate(request.getPromptTemplateCode());
        List<AiTestopsDocumentChunk> chunks = listChunks(document.getDocumentId());

        String generationId = IdGenerator.generationId();
        String inputSnapshotJson = JsonUtil.toJson(objectMapper, buildInputSnapshot(document, requirementExtract, template, chunks, request));
        AiTestopsGenerationRecord record = createProcessingRecord(generationId, document, requirementExtract, template, request, inputSnapshotJson);
        generationRecordService.save(record);
        log.info("测试用例生成记录创建: documentId={}, generationId={}", document.getDocumentId(), generationId);

        try {
            AiChatResponse response = aiClient.chat(AiChatRequest.builder()
                    .modelCode(resolveModelCode(request.getModelCode()))
                    .modelName(aiModelProperties.getModelName())
                    .systemPrompt(template.getPromptContent())
                    .userPrompt(buildUserPrompt(document, requirementExtract, chunks))
                    .generationType(GenerationTypeEnum.TEST_CASE_GENERATE.name())
                    .build());
            log.info("AI 原始返回内容长度: generationId={}, length={}", generationId, response.getContent().length());

            JsonNode root = parseJsonOrSaveSchemaFailure(generationId, response.getContent());
            List<String> validationErrors = validateAndSaveResults(generationId, root);
            if (!validationErrors.isEmpty()) {
                updateGenerationFailed(generationId, response.getRawResponseJson(), "AI 输出校验失败");
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "AI 输出校验失败: " + String.join("; ", validationErrors));
            }

            List<AiTestopsTestCaseDraft> drafts = saveDrafts(document, requirementExtract, generationId, root);
            updateGenerationSuccess(generationId, response);
            log.info("测试用例草稿保存数量: generationId={}, draftCount={}", generationId, drafts.size());

            TestCaseGenerateVO vo = new TestCaseGenerateVO();
            vo.setGenerationId(generationId);
            vo.setDocumentId(document.getDocumentId());
            vo.setRequirementExtractId(requirementExtract == null ? null : requirementExtract.getRequirementExtractId());
            vo.setValidationStatus(ValidationStatusEnum.PASSED.name());
            vo.setDraftCount(drafts.size());
            vo.setDrafts(drafts.stream().map(this::toVO).toList());
            return vo;
        } catch (BusinessException ex) {
            if (!GenerationStatusEnum.FAILED.name().equals(getGenerationStatus(generationId))) {
                updateGenerationFailed(generationId, null, ex.getMessage());
            }
            log.error("测试用例生成失败: generationId={}, message={}", generationId, ex.getMessage(), ex);
            throw ex;
        } catch (Exception ex) {
            updateGenerationFailed(generationId, null, ex.getMessage());
            log.error("测试用例生成失败: generationId={}", generationId, ex);
            throw new BusinessException(ErrorCode.AI_CALL_FAILED, "测试用例生成失败: " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<TestCaseDraftVO> listDrafts(String documentId, String generationId) {
        LambdaQueryWrapper<AiTestopsTestCaseDraft> wrapper = new LambdaQueryWrapper<AiTestopsTestCaseDraft>()
                .orderByDesc(AiTestopsTestCaseDraft::getCreatedAt);
        if (StringUtils.hasText(documentId)) {
            wrapper.eq(AiTestopsTestCaseDraft::getDocumentId, documentId);
        }
        if (StringUtils.hasText(generationId)) {
            wrapper.eq(AiTestopsTestCaseDraft::getGenerationId, generationId);
        }
        return list(wrapper).stream().map(this::toVO).toList();
    }

    @Override
    public TestCaseDraftVO getDraft(String draftCaseId) {
        return toVO(requireDraft(draftCaseId));
    }

    @Override
    @Transactional
    public TestCaseDraftVO updateDraft(String draftCaseId, TestCaseDraftUpdateRequest request) {
        AiTestopsTestCaseDraft draft = requireDraft(draftCaseId);
        assertDraftEditable(draft);
        String beforeJson = JsonUtil.toJson(objectMapper, toVO(draft));
        applyDraftUpdates(draft, request);
        draft.setRawCaseJson(buildRawCaseJson(draft));
        draft.setUpdatedAt(LocalDateTime.now());
        updateById(draft);
        reviewRecordService.record(draft.getCaseId(), draft.getDraftCaseId(), null, ReviewActionEnum.EDIT,
                beforeJson, JsonUtil.toJson(objectMapper, toVO(draft)), null, request == null ? null : request.getReviewer());
        log.info("人工编辑测试用例草稿: draftCaseId={}, caseId={}", draft.getDraftCaseId(), draft.getCaseId());
        return toVO(draft);
    }

    @Override
    @Transactional
    public TestCaseVO approveDraft(String draftCaseId, TestCaseDraftReviewRequest request) {
        AiTestopsTestCaseDraft draft = requireDraft(draftCaseId);
        assertDraftEditable(draft);
        String beforeJson = JsonUtil.toJson(objectMapper, toVO(draft));
        LocalDateTime now = LocalDateTime.now();
        AiTestopsTestCase testCase = buildFormalCase(draft, now);
        testCaseService.save(testCase);
        saveRequirementMappings(draft, testCase, now);

        draft.setReviewStatus(ReviewStatusEnum.APPROVED.name());
        draft.setUpdatedAt(now);
        updateById(draft);

        TestCaseVO vo = toCaseVO(testCase);
        reviewRecordService.record(draft.getCaseId(), draft.getDraftCaseId(), testCase.getTestCaseId(),
                ReviewActionEnum.APPROVE, beforeJson, JsonUtil.toJson(objectMapper, vo),
                request == null ? null : request.getReason(), request == null ? null : request.getReviewer());
        log.info("人工确认测试用例草稿: draftCaseId={}, testCaseId={}", draft.getDraftCaseId(), testCase.getTestCaseId());
        return vo;
    }

    @Override
    @Transactional
    public TestCaseDraftVO rejectDraft(String draftCaseId, TestCaseDraftReviewRequest request) {
        AiTestopsTestCaseDraft draft = requireDraft(draftCaseId);
        assertDraftEditable(draft);
        String beforeJson = JsonUtil.toJson(objectMapper, toVO(draft));
        draft.setReviewStatus(ReviewStatusEnum.REJECTED.name());
        draft.setUpdatedAt(LocalDateTime.now());
        updateById(draft);
        reviewRecordService.record(draft.getCaseId(), draft.getDraftCaseId(), null, ReviewActionEnum.REJECT,
                beforeJson, JsonUtil.toJson(objectMapper, toVO(draft)),
                request == null ? null : request.getReason(), request == null ? null : request.getReviewer());
        log.info("人工驳回测试用例草稿: draftCaseId={}, reason={}", draft.getDraftCaseId(),
                request == null ? null : request.getReason());
        return toVO(draft);
    }

    @Override
    @Transactional
    public List<TestCaseVO> batchApprove(TestCaseDraftBatchApproveRequest request) {
        if (request == null || request.getDraftCaseIds() == null || request.getDraftCaseIds().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "draftCaseIds 不能为空");
        }
        List<TestCaseVO> approved = new ArrayList<>();
        for (String draftCaseId : request.getDraftCaseIds()) {
            TestCaseDraftReviewRequest reviewRequest = new TestCaseDraftReviewRequest();
            reviewRequest.setReviewer(request.getReviewer());
            reviewRequest.setReason("批量确认");
            approved.add(approveDraft(draftCaseId, reviewRequest));
        }
        log.info("批量确认测试用例草稿完成: count={}", approved.size());
        return approved;
    }

    private void validateGenerateRequest(TestCaseGenerateRequest request) {
        if (!StringUtils.hasText(request.getDocumentId()) && !StringUtils.hasText(request.getRequirementExtractId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "documentId 和 requirementExtractId 至少传一个");
        }
        if (!StringUtils.hasText(request.getPromptTemplateCode())) {
            request.setPromptTemplateCode("TEST_CASE_GENERATE");
        }
    }

    private AiTestopsRequirementExtract resolveRequirementExtract(TestCaseGenerateRequest request) {
        if (StringUtils.hasText(request.getRequirementExtractId())) {
            AiTestopsRequirementExtract extract = requirementExtractService.getOne(new LambdaQueryWrapper<AiTestopsRequirementExtract>()
                    .eq(AiTestopsRequirementExtract::getRequirementExtractId, request.getRequirementExtractId())
                    .last("limit 1"), false);
            if (extract == null) {
                throw new BusinessException(ErrorCode.REQUIREMENT_EXTRACT_NOT_FOUND,
                        "需求解析结果不存在: " + request.getRequirementExtractId());
            }
            return extract;
        }
        if (StringUtils.hasText(request.getDocumentId())) {
            return requirementExtractService.getOne(new LambdaQueryWrapper<AiTestopsRequirementExtract>()
                    .eq(AiTestopsRequirementExtract::getDocumentId, request.getDocumentId())
                    .orderByDesc(AiTestopsRequirementExtract::getCreatedAt)
                    .last("limit 1"), false);
        }
        return null;
    }

    private AiTestopsDocument resolveParsedDocument(TestCaseGenerateRequest request, AiTestopsRequirementExtract extract) {
        String documentId = extract == null ? request.getDocumentId() : extract.getDocumentId();
        AiTestopsDocument document = documentService.getOne(new LambdaQueryWrapper<AiTestopsDocument>()
                .eq(AiTestopsDocument::getDocumentId, documentId)
                .last("limit 1"), false);
        if (document == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND, "文档不存在: " + documentId);
        }
        if (!ParseStatusEnum.SUCCESS.name().equals(document.getParseStatus())) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_PARSED, "文档未解析成功不能生成测试用例: " + documentId);
        }
        return document;
    }

    private List<AiTestopsDocumentChunk> listChunks(String documentId) {
        return documentChunkService.list(new LambdaQueryWrapper<AiTestopsDocumentChunk>()
                .eq(AiTestopsDocumentChunk::getDocumentId, documentId)
                .orderByAsc(AiTestopsDocumentChunk::getChunkIndex));
    }

    private AiTestopsGenerationRecord createProcessingRecord(String generationId, AiTestopsDocument document,
                                                             AiTestopsRequirementExtract extract, AiTestopsPromptTemplate template,
                                                             TestCaseGenerateRequest request, String inputSnapshotJson) {
        LocalDateTime now = LocalDateTime.now();
        AiTestopsGenerationRecord record = new AiTestopsGenerationRecord();
        record.setGenerationId(generationId);
        record.setDocumentId(document.getDocumentId());
        record.setRequirementExtractId(extract == null ? null : extract.getRequirementExtractId());
        record.setPromptTemplateCode(template.getTemplateCode());
        record.setModelCode(resolveModelCode(request.getModelCode()));
        record.setModelName(aiModelProperties.getModelName());
        record.setGenerationType(GenerationTypeEnum.TEST_CASE_GENERATE.name());
        record.setInputSnapshotJson(inputSnapshotJson);
        record.setStatus(GenerationStatusEnum.PROCESSING.name());
        record.setStartedAt(now);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        return record;
    }

    private Map<String, Object> buildInputSnapshot(AiTestopsDocument document, AiTestopsRequirementExtract extract,
                                                   AiTestopsPromptTemplate template, List<AiTestopsDocumentChunk> chunks,
                                                   TestCaseGenerateRequest request) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("documentId", document.getDocumentId());
        snapshot.put("requirementExtractId", extract == null ? null : extract.getRequirementExtractId());
        snapshot.put("promptTemplateCode", template.getTemplateCode());
        snapshot.put("modelCode", resolveModelCode(request.getModelCode()));
        snapshot.put("requirementsJson", extract == null ? null : extract.getRequirementsJson());
        snapshot.put("chunks", chunks.stream().map(chunk -> Map.of(
                "chunk_id", chunk.getChunkId(),
                "chunk_index", chunk.getChunkIndex(),
                "chunk_text", chunk.getChunkText()
        )).toList());
        return snapshot;
    }

    private String buildUserPrompt(AiTestopsDocument document, AiTestopsRequirementExtract extract, List<AiTestopsDocumentChunk> chunks) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("document_id", document.getDocumentId());
        input.put("title", document.getTitle());
        input.put("requirements", extract == null ? null : extract.getRequirementsJson());
        input.put("business_rules", extract == null ? null : extract.getBusinessRulesJson());
        input.put("api_list", extract == null ? null : extract.getApiListJson());
        input.put("field_constraints", extract == null ? null : extract.getFieldConstraintsJson());
        input.put("exception_cases", extract == null ? null : extract.getExceptionCasesJson());
        input.put("risks", extract == null ? null : extract.getRisksJson());
        input.put("chunks", chunks.stream().map(chunk -> Map.of(
                "chunk_id", chunk.getChunkId(),
                "chunk_index", chunk.getChunkIndex(),
                "chunk_text", chunk.getChunkText()
        )).toList());
        return "请基于以下结构化需求或文档 chunks 生成测试用例：\n" + JsonUtil.toJson(objectMapper, input);
    }

    private JsonNode parseJsonOrSaveSchemaFailure(String generationId, String content) {
        try {
            JsonNode root = objectMapper.readTree(content);
            JsonNode testCases = root.get("test_cases");
            if (testCases == null || !testCases.isArray() || testCases.isEmpty()) {
                saveValidation(generationId, ValidationTypeEnum.SCHEMA, ValidationStatusEnum.FAILED,
                        List.of("test_cases 字段缺失、不是数组或为空"));
                throw new BusinessException(ErrorCode.AI_OUTPUT_INVALID, "AI 输出 test_cases 字段缺失、不是数组或为空");
            }
            return root;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            saveValidation(generationId, ValidationTypeEnum.SCHEMA, ValidationStatusEnum.FAILED,
                    List.of("AI 输出不是合法 JSON: " + ex.getMessage()));
            throw new BusinessException(ErrorCode.AI_OUTPUT_INVALID, "AI 输出不是合法 JSON: " + ex.getMessage(), ex);
        }
    }

    private List<String> validateAndSaveResults(String generationId, JsonNode root) {
        List<String> errors = new ArrayList<>();
        JsonNode testCases = root.get("test_cases");

        List<String> requiredFieldErrors = validateRequiredFields(testCases);
        List<String> formatErrors = validateFormat(testCases);
        List<String> duplicateErrors = validateDuplicates(testCases);

        saveValidation(generationId, ValidationTypeEnum.SCHEMA, ValidationStatusEnum.PASSED, List.of());
        saveValidation(generationId, ValidationTypeEnum.REQUIRED_FIELD,
                requiredFieldErrors.isEmpty() ? ValidationStatusEnum.PASSED : ValidationStatusEnum.FAILED, requiredFieldErrors);
        saveValidation(generationId, ValidationTypeEnum.FORMAT,
                formatErrors.isEmpty() ? ValidationStatusEnum.PASSED : ValidationStatusEnum.FAILED, formatErrors);
        saveValidation(generationId, ValidationTypeEnum.DUPLICATE,
                duplicateErrors.isEmpty() ? ValidationStatusEnum.PASSED : ValidationStatusEnum.FAILED, duplicateErrors);

        errors.addAll(requiredFieldErrors);
        errors.addAll(formatErrors);
        errors.addAll(duplicateErrors);
        log.info("校验结果: generationId={}, passed={}, errorCount={}", generationId, errors.isEmpty(), errors.size());
        return errors;
    }

    private List<String> validateRequiredFields(JsonNode testCases) {
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < testCases.size(); i++) {
            JsonNode item = testCases.get(i);
            String prefix = "test_cases[" + i + "]";
            requireText(item, "case_id", prefix, errors);
            requireText(item, "title", prefix, errors);
            requireText(item, "priority", prefix, errors);
            requireArray(item, "steps", prefix, errors);
            requireArray(item, "requirement_refs", prefix, errors);
        }
        return errors;
    }

    private List<String> validateFormat(JsonNode testCases) {
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < testCases.size(); i++) {
            JsonNode steps = testCases.get(i).get("steps");
            if (steps == null || !steps.isArray()) {
                continue;
            }
            for (int j = 0; j < steps.size(); j++) {
                JsonNode step = steps.get(j);
                String prefix = "test_cases[" + i + "].steps[" + j + "]";
                requireText(step, "action", prefix, errors);
                requireText(step, "expected_result", prefix, errors);
            }
        }
        return errors;
    }

    private List<String> validateDuplicates(JsonNode testCases) {
        List<String> errors = new ArrayList<>();
        Set<String> caseIds = new HashSet<>();
        Set<String> titles = new HashSet<>();
        for (JsonNode item : testCases) {
            String caseId = textValue(item, "case_id");
            String title = textValue(item, "title");
            if (StringUtils.hasText(caseId) && !caseIds.add(caseId)) {
                errors.add("case_id 重复: " + caseId);
            }
            if (StringUtils.hasText(title) && !titles.add(title)) {
                errors.add("title 重复: " + title);
            }
        }
        return errors;
    }

    private void requireText(JsonNode node, String field, String prefix, List<String> errors) {
        if (!StringUtils.hasText(textValue(node, field))) {
            errors.add(prefix + "." + field + " 不能为空");
        }
    }

    private void requireArray(JsonNode node, String field, String prefix, List<String> errors) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray() || value.isEmpty()) {
            errors.add(prefix + "." + field + " 必须是非空数组");
        }
    }

    private String textValue(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private void saveValidation(String generationId, ValidationTypeEnum type, ValidationStatusEnum status, List<String> errors) {
        AiTestopsValidationResult result = new AiTestopsValidationResult();
        result.setValidationId(IdGenerator.validationId());
        result.setGenerationId(generationId);
        result.setValidationType(type.name());
        result.setStatus(status.name());
        result.setErrorDetailJson(JsonUtil.toJson(objectMapper, errors));
        result.setWarningDetailJson("[]");
        result.setCreatedAt(LocalDateTime.now());
        validationResultService.save(result);
    }

    private List<AiTestopsTestCaseDraft> saveDrafts(AiTestopsDocument document, AiTestopsRequirementExtract extract,
                                                    String generationId, JsonNode root) {
        LocalDateTime now = LocalDateTime.now();
        List<AiTestopsTestCaseDraft> drafts = new ArrayList<>();
        for (JsonNode item : root.get("test_cases")) {
            AiTestopsTestCaseDraft draft = new AiTestopsTestCaseDraft();
            draft.setDraftCaseId(IdGenerator.draftCaseId());
            draft.setCaseId(textValue(item, "case_id"));
            draft.setGenerationId(generationId);
            draft.setDocumentId(document.getDocumentId());
            draft.setRequirementExtractId(extract == null ? null : extract.getRequirementExtractId());
            draft.setTitle(textValue(item, "title"));
            draft.setPreconditionsJson(jsonField(item, "preconditions", "[]"));
            draft.setStepsJson(jsonField(item, "steps", "[]"));
            draft.setPriority(textValue(item, "priority"));
            draft.setCaseType(textValue(item, "case_type"));
            draft.setRiskLevel(textValue(item, "risk_level"));
            draft.setRequirementRefsJson(jsonField(item, "requirement_refs", "[]"));
            draft.setRiskTagsJson(jsonField(item, "risk_tags", "[]"));
            draft.setReviewStatus(ReviewStatusEnum.PENDING.name());
            draft.setRawCaseJson(item.toString());
            draft.setCreatedAt(now);
            draft.setUpdatedAt(now);
            drafts.add(draft);
        }
        saveBatch(drafts);
        return drafts;
    }

    private String jsonField(JsonNode item, String field, String defaultValue) {
        JsonNode value = item.get(field);
        return value == null || value.isNull() ? defaultValue : value.toString();
    }

    private AiTestopsTestCaseDraft requireDraft(String draftCaseId) {
        AiTestopsTestCaseDraft draft = getOne(new LambdaQueryWrapper<AiTestopsTestCaseDraft>()
                .eq(AiTestopsTestCaseDraft::getDraftCaseId, draftCaseId)
                .last("limit 1"), false);
        if (draft == null) {
            throw new BusinessException(ErrorCode.TEST_CASE_DRAFT_NOT_FOUND, "测试用例草稿不存在: " + draftCaseId);
        }
        return draft;
    }

    private void assertDraftEditable(AiTestopsTestCaseDraft draft) {
        if (ReviewStatusEnum.APPROVED.name().equals(draft.getReviewStatus())) {
            throw new BusinessException(ErrorCode.TEST_CASE_DRAFT_ALREADY_APPROVED,
                    "已确认的草稿不能重复确认或编辑: " + draft.getDraftCaseId());
        }
    }

    private void applyDraftUpdates(AiTestopsTestCaseDraft draft, TestCaseDraftUpdateRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "编辑内容不能为空");
        }
        if (StringUtils.hasText(request.getTitle())) {
            draft.setTitle(request.getTitle());
        }
        if (StringUtils.hasText(request.getPreconditionsJson())) {
            validateJsonArray(request.getPreconditionsJson(), "preconditionsJson");
            draft.setPreconditionsJson(request.getPreconditionsJson());
        }
        if (StringUtils.hasText(request.getStepsJson())) {
            validateJsonArray(request.getStepsJson(), "stepsJson");
            draft.setStepsJson(request.getStepsJson());
        }
        if (StringUtils.hasText(request.getPriority())) {
            draft.setPriority(request.getPriority());
        }
        if (StringUtils.hasText(request.getCaseType())) {
            draft.setCaseType(request.getCaseType());
        }
        if (StringUtils.hasText(request.getRiskLevel())) {
            draft.setRiskLevel(request.getRiskLevel());
        }
        if (StringUtils.hasText(request.getRequirementRefsJson())) {
            validateJsonArray(request.getRequirementRefsJson(), "requirementRefsJson");
            draft.setRequirementRefsJson(request.getRequirementRefsJson());
        }
        if (StringUtils.hasText(request.getRiskTagsJson())) {
            validateJsonArray(request.getRiskTagsJson(), "riskTagsJson");
            draft.setRiskTagsJson(request.getRiskTagsJson());
        }
        validateDraftRequiredFields(draft);
    }

    private void validateDraftRequiredFields(AiTestopsTestCaseDraft draft) {
        if (!StringUtils.hasText(draft.getTitle())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "测试用例标题不能为空");
        }
        if (!StringUtils.hasText(draft.getPriority())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "测试用例优先级不能为空");
        }
        validateJsonArray(draft.getStepsJson(), "stepsJson");
        validateJsonArray(draft.getRequirementRefsJson(), "requirementRefsJson");
    }

    private void validateJsonArray(String json, String fieldName) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isArray() || node.isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + " 必须是非空 JSON 数组");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + " 不是合法 JSON: " + ex.getMessage(), ex);
        }
    }

    private String buildRawCaseJson(AiTestopsTestCaseDraft draft) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("case_id", draft.getCaseId());
        raw.put("title", draft.getTitle());
        raw.put("preconditions", parseJsonOrDefault(draft.getPreconditionsJson(), List.of()));
        raw.put("steps", parseJsonOrDefault(draft.getStepsJson(), List.of()));
        raw.put("priority", draft.getPriority());
        raw.put("case_type", draft.getCaseType());
        raw.put("risk_level", draft.getRiskLevel());
        raw.put("requirement_refs", parseJsonOrDefault(draft.getRequirementRefsJson(), List.of()));
        raw.put("risk_tags", parseJsonOrDefault(draft.getRiskTagsJson(), List.of()));
        return JsonUtil.toJson(objectMapper, raw);
    }

    private Object parseJsonOrDefault(String json, Object defaultValue) {
        if (!StringUtils.hasText(json)) {
            return defaultValue;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private AiTestopsTestCase buildFormalCase(AiTestopsTestCaseDraft draft, LocalDateTime now) {
        validateDraftRequiredFields(draft);
        AiTestopsTestCase testCase = new AiTestopsTestCase();
        testCase.setTestCaseId(IdGenerator.testCaseId());
        testCase.setSourceDraftCaseId(draft.getDraftCaseId());
        testCase.setCaseId(draft.getCaseId());
        testCase.setGenerationId(draft.getGenerationId());
        testCase.setDocumentId(draft.getDocumentId());
        testCase.setRequirementExtractId(draft.getRequirementExtractId());
        testCase.setTitle(draft.getTitle());
        testCase.setPreconditionsJson(draft.getPreconditionsJson());
        testCase.setStepsJson(draft.getStepsJson());
        testCase.setPriority(draft.getPriority());
        testCase.setCaseType(draft.getCaseType());
        testCase.setRiskLevel(draft.getRiskLevel());
        testCase.setRequirementRefsJson(draft.getRequirementRefsJson());
        testCase.setRiskTagsJson(draft.getRiskTagsJson());
        testCase.setStatus("ACTIVE");
        testCase.setCreatedAt(now);
        testCase.setUpdatedAt(now);
        return testCase;
    }

    private void saveRequirementMappings(AiTestopsTestCaseDraft draft, AiTestopsTestCase testCase, LocalDateTime now) {
        List<AiTestopsRequirementCaseMapping> mappings = new ArrayList<>();
        for (String requirementId : parseRequirementRefs(draft.getRequirementRefsJson())) {
            AiTestopsRequirementCaseMapping mapping = new AiTestopsRequirementCaseMapping();
            mapping.setMappingId(IdGenerator.mappingId());
            mapping.setRequirementId(requirementId);
            mapping.setTestCaseId(testCase.getTestCaseId());
            mapping.setDraftCaseId(draft.getDraftCaseId());
            mapping.setGenerationId(draft.getGenerationId());
            mapping.setDocumentId(draft.getDocumentId());
            mapping.setCreatedAt(now);
            mappings.add(mapping);
        }
        if (!mappings.isEmpty()) {
            requirementCaseMappingService.saveBatch(mappings);
        }
    }

    private List<String> parseRequirementRefs(String requirementRefsJson) {
        try {
            JsonNode node = objectMapper.readTree(requirementRefsJson);
            if (!node.isArray()) {
                return List.of();
            }
            List<String> refs = new ArrayList<>();
            for (JsonNode item : node) {
                if (StringUtils.hasText(item.asText())) {
                    refs.add(item.asText());
                }
            }
            return refs;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private void updateGenerationSuccess(String generationId, AiChatResponse response) {
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
    }

    private void updateGenerationFailed(String generationId, String outputJson, String message) {
        LambdaUpdateWrapper<AiTestopsGenerationRecord> wrapper = new LambdaUpdateWrapper<AiTestopsGenerationRecord>()
                .eq(AiTestopsGenerationRecord::getGenerationId, generationId)
                .set(AiTestopsGenerationRecord::getStatus, GenerationStatusEnum.FAILED.name())
                .set(AiTestopsGenerationRecord::getErrorMessage, truncate(message, 1000))
                .set(AiTestopsGenerationRecord::getFinishedAt, LocalDateTime.now())
                .set(AiTestopsGenerationRecord::getUpdatedAt, LocalDateTime.now());
        if (outputJson != null) {
            wrapper.set(AiTestopsGenerationRecord::getOutputJson, outputJson);
        }
        generationRecordService.update(wrapper);
    }

    private String getGenerationStatus(String generationId) {
        AiTestopsGenerationRecord record = generationRecordService.getOne(new LambdaQueryWrapper<AiTestopsGenerationRecord>()
                .eq(AiTestopsGenerationRecord::getGenerationId, generationId)
                .last("limit 1"), false);
        return record == null ? null : record.getStatus();
    }

    private TestCaseVO toCaseVO(AiTestopsTestCase testCase) {
        TestCaseVO vo = new TestCaseVO();
        vo.setTestCaseId(testCase.getTestCaseId());
        vo.setSourceDraftCaseId(testCase.getSourceDraftCaseId());
        vo.setCaseId(testCase.getCaseId());
        vo.setGenerationId(testCase.getGenerationId());
        vo.setDocumentId(testCase.getDocumentId());
        vo.setRequirementExtractId(testCase.getRequirementExtractId());
        vo.setTitle(testCase.getTitle());
        vo.setPreconditionsJson(testCase.getPreconditionsJson());
        vo.setStepsJson(testCase.getStepsJson());
        vo.setPriority(testCase.getPriority());
        vo.setCaseType(testCase.getCaseType());
        vo.setRiskLevel(testCase.getRiskLevel());
        vo.setRequirementRefsJson(testCase.getRequirementRefsJson());
        vo.setRiskTagsJson(testCase.getRiskTagsJson());
        vo.setStatus(testCase.getStatus());
        vo.setCreatedAt(testCase.getCreatedAt());
        vo.setUpdatedAt(testCase.getUpdatedAt());
        return vo;
    }

    private TestCaseDraftVO toVO(AiTestopsTestCaseDraft draft) {
        TestCaseDraftVO vo = new TestCaseDraftVO();
        vo.setDraftCaseId(draft.getDraftCaseId());
        vo.setCaseId(draft.getCaseId());
        vo.setGenerationId(draft.getGenerationId());
        vo.setDocumentId(draft.getDocumentId());
        vo.setRequirementExtractId(draft.getRequirementExtractId());
        vo.setTitle(draft.getTitle());
        vo.setPreconditionsJson(draft.getPreconditionsJson());
        vo.setStepsJson(draft.getStepsJson());
        vo.setPriority(draft.getPriority());
        vo.setCaseType(draft.getCaseType());
        vo.setRiskLevel(draft.getRiskLevel());
        vo.setRequirementRefsJson(draft.getRequirementRefsJson());
        vo.setRiskTagsJson(draft.getRiskTagsJson());
        vo.setReviewStatus(draft.getReviewStatus());
        vo.setRawCaseJson(draft.getRawCaseJson());
        vo.setCreatedAt(draft.getCreatedAt());
        vo.setUpdatedAt(draft.getUpdatedAt());
        return vo;
    }

    private String resolveModelCode(String requestedModelCode) {
        return StringUtils.hasText(requestedModelCode) ? requestedModelCode : aiModelProperties.getModelCode();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
