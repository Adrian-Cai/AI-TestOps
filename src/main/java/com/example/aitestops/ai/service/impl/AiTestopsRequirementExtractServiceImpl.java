package com.example.aitestops.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.aitestops.ai.client.AiClient;
import com.example.aitestops.ai.dto.AiChatRequest;
import com.example.aitestops.ai.dto.AiChatResponse;
import com.example.aitestops.ai.dto.RequirementExtractRequest;
import com.example.aitestops.ai.entity.AiTestopsGenerationRecord;
import com.example.aitestops.ai.entity.AiTestopsPromptTemplate;
import com.example.aitestops.ai.entity.AiTestopsRequirementExtract;
import com.example.aitestops.ai.mapper.AiTestopsRequirementExtractMapper;
import com.example.aitestops.ai.service.AiTestopsGenerationRecordService;
import com.example.aitestops.ai.service.AiTestopsPromptTemplateService;
import com.example.aitestops.ai.service.AiTestopsRequirementExtractService;
import com.example.aitestops.ai.vo.RequirementExtractVO;
import com.example.aitestops.common.config.AiModelProperties;
import com.example.aitestops.common.enums.GenerationStatusEnum;
import com.example.aitestops.common.enums.GenerationTypeEnum;
import com.example.aitestops.common.enums.ParseStatusEnum;
import com.example.aitestops.common.exception.BusinessException;
import com.example.aitestops.common.exception.ErrorCode;
import com.example.aitestops.common.util.AiJsonExtractor;
import com.example.aitestops.common.util.IdGenerator;
import com.example.aitestops.common.util.JsonUtil;
import com.example.aitestops.document.entity.AiTestopsDocument;
import com.example.aitestops.document.entity.AiTestopsDocumentChunk;
import com.example.aitestops.document.service.AiTestopsDocumentChunkService;
import com.example.aitestops.document.service.AiTestopsDocumentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 需求解析服务实现，负责调用模型并保存生成记录和结构化结果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiTestopsRequirementExtractServiceImpl
        extends ServiceImpl<AiTestopsRequirementExtractMapper, AiTestopsRequirementExtract>
        implements AiTestopsRequirementExtractService {

    private final AiTestopsDocumentService documentService;
    private final AiTestopsDocumentChunkService documentChunkService;
    private final AiTestopsPromptTemplateService promptTemplateService;
    private final AiTestopsGenerationRecordService generationRecordService;
    private final AiClient aiClient;
    private final AiModelProperties aiModelProperties;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public RequirementExtractVO extractRequirements(RequirementExtractRequest request) {
        AiTestopsDocument document = getParsedDocument(request.getDocumentId());
        AiTestopsPromptTemplate template = promptTemplateService.getEnabledTemplate(request.getPromptTemplateCode());
        List<AiTestopsDocumentChunk> chunks = listChunks(document.getDocumentId());
        if (chunks.isEmpty()) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_PARSED, "文档没有 chunks，请先解析文档: " + document.getDocumentId());
        }

        String generationId = null;
        try {
            generationId = IdGenerator.generationId();
            LocalDateTime now = LocalDateTime.now();
            String inputSnapshotJson = JsonUtil.toJson(objectMapper, buildInputSnapshot(document, template, chunks, request));
            AiTestopsGenerationRecord record = createProcessingRecord(generationId, document.getDocumentId(), template, request, inputSnapshotJson, now);
            generationRecordService.save(record);
            log.info("需求解析生成记录创建: documentId={}, generationId={}", document.getDocumentId(), generationId);

            String userPrompt = buildUserPrompt(document, chunks);
            AiChatResponse response = aiClient.chat(AiChatRequest.builder()
                    .modelCode(resolveModelCode(request.getModelCode()))
                    .modelName(aiModelProperties.getModelName())
                    .systemPrompt(template.getPromptContent())
                    .userPrompt(userPrompt)
                    .generationType(GenerationTypeEnum.REQUIREMENT_EXTRACT.name())
                    .build());

            log.info("AI 原始返回内容长度: generationId={}, length={}", generationId, response.getContent().length());
            JsonNode root = parseAndValidateRequirementJson(response.getContent(), generationId);
            AiTestopsRequirementExtract extract = saveRequirementExtract(document.getDocumentId(), generationId, root, response.getContent());
            updateGenerationSuccess(generationId, extract.getRequirementExtractId(), response);
            log.info("需求解析完成: documentId={}, generationId={}, requirementExtractId={}",
                    document.getDocumentId(), generationId, extract.getRequirementExtractId());
            return toVO(extract);
        } catch (BusinessException ex) {
            updateGenerationFailed(generationId, ex.getMessage());
            log.error("需求解析失败: documentId={}, generationId={}, message={}", document.getDocumentId(), generationId, ex.getMessage(), ex);
            throw ex;
        } catch (Exception ex) {
            updateGenerationFailed(generationId, ex.getMessage());
            log.error("需求解析失败: documentId={}, generationId={}", document.getDocumentId(), generationId, ex);
            throw new BusinessException(ErrorCode.AI_CALL_FAILED, "需求解析失败: " + ex.getMessage(), ex);
        }
    }

    private AiTestopsDocument getParsedDocument(String documentId) {
        AiTestopsDocument document = documentService.getOne(new LambdaQueryWrapper<AiTestopsDocument>()
                .eq(AiTestopsDocument::getDocumentId, documentId)
                .last("limit 1"), false);
        if (document == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND, "文档不存在: " + documentId);
        }
        if (!ParseStatusEnum.SUCCESS.name().equals(document.getParseStatus())) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_PARSED, "文档未解析成功不能进行需求解析: " + documentId);
        }
        return document;
    }

    private List<AiTestopsDocumentChunk> listChunks(String documentId) {
        return documentChunkService.list(new LambdaQueryWrapper<AiTestopsDocumentChunk>()
                .eq(AiTestopsDocumentChunk::getDocumentId, documentId)
                .orderByAsc(AiTestopsDocumentChunk::getChunkIndex));
    }

    private AiTestopsGenerationRecord createProcessingRecord(String generationId, String documentId, AiTestopsPromptTemplate template,
                                                             RequirementExtractRequest request, String inputSnapshotJson,
                                                             LocalDateTime now) {
        AiTestopsGenerationRecord record = new AiTestopsGenerationRecord();
        record.setGenerationId(generationId);
        record.setDocumentId(documentId);
        record.setPromptTemplateCode(template.getTemplateCode());
        record.setPromptTemplateVersion(template.getVersion());
        record.setModelCode(resolveModelCode(request.getModelCode()));
        record.setModelName(aiModelProperties.getModelName());
        record.setGenerationType(GenerationTypeEnum.REQUIREMENT_EXTRACT.name());
        record.setInputSnapshotJson(inputSnapshotJson);
        record.setStatus(GenerationStatusEnum.PROCESSING.name());
        record.setStartedAt(now);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        return record;
    }

    private Map<String, Object> buildInputSnapshot(AiTestopsDocument document, AiTestopsPromptTemplate template,
                                                   List<AiTestopsDocumentChunk> chunks, RequirementExtractRequest request) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("documentId", document.getDocumentId());
        snapshot.put("title", document.getTitle());
        snapshot.put("promptTemplateCode", template.getTemplateCode());
        snapshot.put("promptTemplateVersion", template.getVersion());
        snapshot.put("modelCode", resolveModelCode(request.getModelCode()));
        snapshot.put("chunks", chunks.stream().map(chunk -> {
            Map<String, Object> chunkMap = new LinkedHashMap<>();
            chunkMap.put("chunk_id", chunk.getChunkId());
            chunkMap.put("chunk_index", chunk.getChunkIndex());
            chunkMap.put("chunk_text", chunk.getChunkText());
            return chunkMap;
        }).toList());
        return snapshot;
    }

    private String buildUserPrompt(AiTestopsDocument document, List<AiTestopsDocumentChunk> chunks) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("document_id", document.getDocumentId());
        input.put("title", document.getTitle());
        input.put("chunks", chunks.stream().map(chunk -> {
            Map<String, Object> chunkMap = new LinkedHashMap<>();
            chunkMap.put("chunk_id", chunk.getChunkId());
            chunkMap.put("chunk_index", chunk.getChunkIndex());
            chunkMap.put("chunk_text", chunk.getChunkText());
            return chunkMap;
        }).toList());
        return "请基于以下文档 chunks 提取结构化需求信息：\n" + JsonUtil.toJson(objectMapper, input);
    }

    private JsonNode parseAndValidateRequirementJson(String content, String generationId) {
        try {
            JsonNode root = objectMapper.readTree(AiJsonExtractor.extractJsonObject(content));
            validateArrayField(root, "requirements", generationId);
            validateArrayField(root, "business_rules", generationId);
            validateArrayField(root, "api_list", generationId);
            validateArrayField(root, "field_constraints", generationId);
            validateArrayField(root, "exception_cases", generationId);
            validateArrayField(root, "risks", generationId);
            log.info("JSON 解析结果: generationId={}, valid=true", generationId);
            return root;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("JSON 解析结果: generationId={}, valid=false", generationId, ex);
            throw new BusinessException(ErrorCode.AI_OUTPUT_INVALID, "AI 输出不是合法 JSON: " + ex.getMessage(), ex);
        }
    }

    private void validateArrayField(JsonNode root, String fieldName, String generationId) {
        JsonNode node = root.get(fieldName);
        if (node == null || !node.isArray()) {
            throw new BusinessException(ErrorCode.AI_OUTPUT_INVALID,
                    "AI 输出字段缺失或类型错误: " + fieldName + ", generationId=" + generationId);
        }
    }

    private AiTestopsRequirementExtract saveRequirementExtract(String documentId, String generationId, JsonNode root, String rawOutputJson) {
        LocalDateTime now = LocalDateTime.now();
        AiTestopsRequirementExtract extract = new AiTestopsRequirementExtract();
        extract.setRequirementExtractId(IdGenerator.requirementExtractId());
        extract.setGenerationId(generationId);
        extract.setDocumentId(documentId);
        extract.setRequirementsJson(root.get("requirements").toString());
        extract.setBusinessRulesJson(root.get("business_rules").toString());
        extract.setApiListJson(root.get("api_list").toString());
        extract.setFieldConstraintsJson(root.get("field_constraints").toString());
        extract.setExceptionCasesJson(root.get("exception_cases").toString());
        extract.setRisksJson(root.get("risks").toString());
        extract.setRawOutputJson(rawOutputJson);
        extract.setCreatedAt(now);
        extract.setUpdatedAt(now);
        save(extract);
        return extract;
    }

    private void updateGenerationSuccess(String generationId, String requirementExtractId, AiChatResponse response) {
        generationRecordService.update(new LambdaUpdateWrapper<AiTestopsGenerationRecord>()
                .eq(AiTestopsGenerationRecord::getGenerationId, generationId)
                .set(AiTestopsGenerationRecord::getRequirementExtractId, requirementExtractId)
                .set(AiTestopsGenerationRecord::getOutputJson, response.getRawResponseJson())
                .set(AiTestopsGenerationRecord::getStatus, GenerationStatusEnum.SUCCESS.name())
                .set(AiTestopsGenerationRecord::getErrorMessage, null)
                .set(AiTestopsGenerationRecord::getTokenInput, response.getPromptTokens())
                .set(AiTestopsGenerationRecord::getTokenOutput, response.getCompletionTokens())
                .set(AiTestopsGenerationRecord::getModelName, response.getModelName())
                .set(AiTestopsGenerationRecord::getFinishedAt, LocalDateTime.now())
                .set(AiTestopsGenerationRecord::getUpdatedAt, LocalDateTime.now()));
    }

    private void updateGenerationFailed(String generationId, String message) {
        if (generationId == null) {
            return;
        }
        generationRecordService.update(new LambdaUpdateWrapper<AiTestopsGenerationRecord>()
                .eq(AiTestopsGenerationRecord::getGenerationId, generationId)
                .set(AiTestopsGenerationRecord::getStatus, GenerationStatusEnum.FAILED.name())
                .set(AiTestopsGenerationRecord::getErrorMessage, truncate(message, 1000))
                .set(AiTestopsGenerationRecord::getFinishedAt, LocalDateTime.now())
                .set(AiTestopsGenerationRecord::getUpdatedAt, LocalDateTime.now()));
    }

    private RequirementExtractVO toVO(AiTestopsRequirementExtract extract) {
        RequirementExtractVO vo = new RequirementExtractVO();
        vo.setRequirementExtractId(extract.getRequirementExtractId());
        vo.setGenerationId(extract.getGenerationId());
        vo.setDocumentId(extract.getDocumentId());
        vo.setRequirementsJson(extract.getRequirementsJson());
        vo.setBusinessRulesJson(extract.getBusinessRulesJson());
        vo.setApiListJson(extract.getApiListJson());
        vo.setFieldConstraintsJson(extract.getFieldConstraintsJson());
        vo.setExceptionCasesJson(extract.getExceptionCasesJson());
        vo.setRisksJson(extract.getRisksJson());
        vo.setRawOutputJson(extract.getRawOutputJson());
        vo.setCreatedAt(extract.getCreatedAt());
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
