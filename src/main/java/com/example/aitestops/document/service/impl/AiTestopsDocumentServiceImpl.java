package com.example.aitestops.document.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.aitestops.common.config.FileUploadProperties;
import com.example.aitestops.common.enums.ParseStatusEnum;
import com.example.aitestops.common.enums.SourceTypeEnum;
import com.example.aitestops.common.exception.BusinessException;
import com.example.aitestops.common.exception.ErrorCode;
import com.example.aitestops.common.util.FileHashUtil;
import com.example.aitestops.common.util.FileNameUtil;
import com.example.aitestops.common.util.IdGenerator;
import com.example.aitestops.common.util.JsonUtil;
import com.example.aitestops.document.dto.TextDocumentCreateRequest;
import com.example.aitestops.document.entity.AiTestopsDocument;
import com.example.aitestops.document.entity.AiTestopsDocumentChunk;
import com.example.aitestops.document.entity.AiTestopsDocumentParseResult;
import com.example.aitestops.document.mapper.AiTestopsDocumentMapper;
import com.example.aitestops.document.service.AiTestopsDocumentChunkService;
import com.example.aitestops.document.service.AiTestopsDocumentParseResultService;
import com.example.aitestops.document.service.AiTestopsDocumentService;
import com.example.aitestops.document.vo.DocumentChunkVO;
import com.example.aitestops.document.vo.DocumentParseSummaryVO;
import com.example.aitestops.document.vo.DocumentVO;
import com.example.aitestops.parser.dto.ChunkData;
import com.example.aitestops.parser.dto.DocumentSection;
import com.example.aitestops.parser.dto.ParsedDocument;
import com.example.aitestops.parser.service.DocumentChunkService;
import com.example.aitestops.parser.service.TextCleanService;
import com.example.aitestops.parser.service.TikaParseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 文档业务服务实现，聚合文件接入、Tika 解析和解析结果入库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiTestopsDocumentServiceImpl
        extends ServiceImpl<AiTestopsDocumentMapper, AiTestopsDocument>
        implements AiTestopsDocumentService {

    private static final int SUMMARY_LENGTH = 300;
    private static final DateTimeFormatter DATE_DIR_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final FileUploadProperties fileUploadProperties;
    private final TikaParseService tikaParseService;
    private final TextCleanService textCleanService;
    private final DocumentChunkService documentChunkService;
    private final AiTestopsDocumentChunkService documentChunkDataService;
    private final AiTestopsDocumentParseResultService parseResultService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public DocumentVO createTextDocument(TextDocumentCreateRequest request) {
        log.info("文本建档开始: title={}", request.getTitle());
        LocalDateTime now = LocalDateTime.now();
        AiTestopsDocument document = new AiTestopsDocument();
        document.setDocumentId(IdGenerator.documentId());
        document.setTitle(request.getTitle().trim());
        document.setSourceType(SourceTypeEnum.TEXT.name());
        document.setRawText(request.getContent().trim());
        document.setParseStatus(ParseStatusEnum.PENDING.name());
        document.setUploadedAt(now);
        document.setCreatedAt(now);
        document.setUpdatedAt(now);

        if (!save(document)) {
            throw new BusinessException(ErrorCode.DATABASE_SAVE_FAILED, "文本 document 保存失败");
        }
        log.info("文本建档完成: documentId={}", document.getDocumentId());
        return toDocumentVO(document);
    }

    @Override
    @Transactional
    public DocumentVO uploadDocument(MultipartFile file, String title) {
        log.info("文件上传开始: originalFilename={}, size={}", file == null ? null : file.getOriginalFilename(), file == null ? null : file.getSize());
        validateFile(file);

        String originalFilename = FileNameUtil.cleanOriginalFilename(file.getOriginalFilename());
        String extension = FileNameUtil.extension(originalFilename);
        String documentId = IdGenerator.documentId();
        String fileHash = calculateFileHash(file);
        AiTestopsDocument duplicate = findDuplicate(fileHash);
        Path savedPath = saveUploadedFile(file, documentId, originalFilename);

        LocalDateTime now = LocalDateTime.now();
        AiTestopsDocument document = new AiTestopsDocument();
        document.setDocumentId(documentId);
        document.setTitle(StringUtils.hasText(title) ? title.trim() : originalFilename);
        document.setSourceType(SourceTypeEnum.FILE.name());
        document.setFileName(originalFilename);
        document.setFileType(extension);
        document.setFilePath(savedPath.toString());
        document.setFileSize(file.getSize());
        document.setFileHash(fileHash);
        document.setDuplicateDocumentId(duplicate == null ? null : duplicate.getDocumentId());
        document.setParseStatus(ParseStatusEnum.PENDING.name());
        document.setUploadedAt(now);
        document.setCreatedAt(now);
        document.setUpdatedAt(now);

        if (!save(document)) {
            throw new BusinessException(ErrorCode.DATABASE_SAVE_FAILED, "上传 document 保存失败");
        }
        log.info("文件上传完成: documentId={}, fileHash={}, duplicateDocumentId={}",
                documentId, fileHash, document.getDuplicateDocumentId());
        return toDocumentVO(document);
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public DocumentParseSummaryVO parseDocument(String documentId) {
        AiTestopsDocument document = getRequiredDocument(documentId);
        log.info("文档解析开始: documentId={}, sourceType={}", documentId, document.getSourceType());
        updateParseStatus(documentId, ParseStatusEnum.PROCESSING, null, null);

        try {
            ParsedDocument parsedDocument = parseBySourceType(document);
            String cleanedText = textCleanService.clean(parsedDocument.getRawText());
            List<DocumentSection> sections = documentChunkService.splitSections(cleanedText);
            List<ChunkData> chunkDataList = documentChunkService.splitChunks(cleanedText);
            log.info("chunk 切分数量: documentId={}, chunkCount={}", documentId, chunkDataList.size());

            String metadataJson = JsonUtil.toJson(objectMapper, parsedDocument.getMetadata());
            String sectionsJson = JsonUtil.toJson(objectMapper, sections);
            String chunksJson = JsonUtil.toJson(objectMapper, chunkDataList);
            String parseResultJson = JsonUtil.toJson(objectMapper, buildParseResult(document, cleanedText, parsedDocument.getMetadata(), sections, chunkDataList));

            removeOldParseData(documentId);
            saveChunks(documentId, chunkDataList);
            saveParseResult(documentId, metadataJson, sectionsJson, chunksJson, parseResultJson, cleanedText.length(), chunkDataList.size());
            updateParseSuccess(documentId, cleanedText, metadataJson);

            log.info("文档解析成功: documentId={}, rawTextLength={}, chunkCount={}", documentId, cleanedText.length(), chunkDataList.size());
            DocumentParseSummaryVO summaryVO = new DocumentParseSummaryVO();
            summaryVO.setDocumentId(documentId);
            summaryVO.setParseStatus(ParseStatusEnum.SUCCESS.name());
            summaryVO.setRawTextLength(cleanedText.length());
            summaryVO.setChunkCount(chunkDataList.size());
            summaryVO.setMetadataJson(metadataJson);
            return summaryVO;
        } catch (BusinessException ex) {
            updateParseStatus(documentId, ParseStatusEnum.FAILED, truncate(ex.getMessage(), 1000), LocalDateTime.now());
            log.error("文档解析失败: documentId={}, message={}", documentId, ex.getMessage(), ex);
            throw ex;
        } catch (Exception ex) {
            updateParseStatus(documentId, ParseStatusEnum.FAILED, truncate(ex.getMessage(), 1000), LocalDateTime.now());
            log.error("文档解析失败: documentId={}", documentId, ex);
            throw new BusinessException(ErrorCode.TIKA_PARSE_FAILED, "文档解析失败: " + ex.getMessage(), ex);
        }
    }

    @Override
    public DocumentVO getDocumentDetail(String documentId) {
        log.info("查询文档详情: documentId={}", documentId);
        return toDocumentVO(getRequiredDocument(documentId));
    }

    @Override
    public List<DocumentChunkVO> listDocumentChunks(String documentId) {
        getRequiredDocument(documentId);
        log.info("查询文档分块: documentId={}", documentId);
        return documentChunkDataService.list(new LambdaQueryWrapper<AiTestopsDocumentChunk>()
                        .eq(AiTestopsDocumentChunk::getDocumentId, documentId)
                        .orderByAsc(AiTestopsDocumentChunk::getChunkIndex))
                .stream()
                .map(this::toChunkVO)
                .toList();
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }
        if (file.getSize() > fileUploadProperties.getMaxSize().toBytes()) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE,
                    "文件超过大小限制: max=" + fileUploadProperties.getMaxSize());
        }
        String filename = FileNameUtil.cleanOriginalFilename(file.getOriginalFilename());
        String extension = FileNameUtil.extension(filename);
        boolean allowed = fileUploadProperties.getAllowedExtensions().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> Objects.equals(value, extension));
        log.info("文件格式校验结果: fileName={}, extension={}, allowed={}", filename, extension, allowed);
        if (!StringUtils.hasText(extension) || !allowed) {
            throw new BusinessException(ErrorCode.FILE_TYPE_UNSUPPORTED,
                    "文件类型不支持，仅支持: " + fileUploadProperties.getAllowedExtensions());
        }
    }

    private String calculateFileHash(MultipartFile file) {
        try {
            String fileHash = FileHashUtil.sha256(file.getInputStream());
            log.info("file_hash 计算结果: originalFilename={}, fileHash={}", file.getOriginalFilename(), fileHash);
            return fileHash;
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.FILE_SAVE_FAILED, "file_hash 计算失败", ex);
        }
    }

    private AiTestopsDocument findDuplicate(String fileHash) {
        return getOne(new LambdaQueryWrapper<AiTestopsDocument>()
                .eq(AiTestopsDocument::getFileHash, fileHash)
                .last("limit 1"), false);
    }

    private Path saveUploadedFile(MultipartFile file, String documentId, String originalFilename) {
        try {
            Path baseDir = Path.of(fileUploadProperties.getUploadDir()).toAbsolutePath().normalize();
            Path dateDir = baseDir.resolve(LocalDate.now().format(DATE_DIR_FORMATTER)).normalize();
            Files.createDirectories(dateDir);
            Path target = dateDir.resolve(documentId + "_" + originalFilename).normalize();
            if (!target.startsWith(baseDir)) {
                throw new BusinessException(ErrorCode.FILE_SAVE_FAILED, "文件保存路径非法");
            }
            file.transferTo(target);
            log.info("原始文件保存成功: documentId={}, path={}", documentId, target);
            return target;
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.FILE_SAVE_FAILED, "文件保存失败: " + ex.getMessage(), ex);
        }
    }

    private ParsedDocument parseBySourceType(AiTestopsDocument document) {
        if (SourceTypeEnum.TEXT.name().equals(document.getSourceType())) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source_type", SourceTypeEnum.TEXT.name());
            metadata.put("title", document.getTitle());
            return new ParsedDocument(document.getRawText(), metadata);
        }
        if (!StringUtils.hasText(document.getFilePath())) {
            throw new BusinessException(ErrorCode.FILE_SAVE_FAILED, "文件路径为空，无法解析");
        }
        return tikaParseService.parse(Path.of(document.getFilePath()), document.getFileName());
    }

    private void removeOldParseData(String documentId) {
        documentChunkDataService.remove(new LambdaQueryWrapper<AiTestopsDocumentChunk>()
                .eq(AiTestopsDocumentChunk::getDocumentId, documentId));
        parseResultService.remove(new LambdaQueryWrapper<AiTestopsDocumentParseResult>()
                .eq(AiTestopsDocumentParseResult::getDocumentId, documentId));
    }

    private void saveChunks(String documentId, List<ChunkData> chunkDataList) {
        LocalDateTime now = LocalDateTime.now();
        List<AiTestopsDocumentChunk> chunks = chunkDataList.stream().map(chunkData -> {
            AiTestopsDocumentChunk chunk = new AiTestopsDocumentChunk();
            chunk.setChunkId(chunkData.getChunkId());
            chunk.setDocumentId(documentId);
            chunk.setChunkIndex(chunkData.getChunkIndex());
            chunk.setSectionTitle(chunkData.getSectionTitle());
            chunk.setChunkText(chunkData.getChunkText());
            chunk.setTokenCount(chunkData.getTokenCount());
            chunk.setExtraJson("{}");
            chunk.setCreatedAt(now);
            return chunk;
        }).toList();
        if (!chunks.isEmpty()) {
            documentChunkDataService.saveBatch(chunks);
        }
        log.info("document_chunk 保存完成: documentId={}, chunkCount={}", documentId, chunks.size());
    }

    private void saveParseResult(String documentId, String metadataJson, String sectionsJson, String chunksJson,
                                 String parseResultJson, int rawTextLength, int chunkCount) {
        LocalDateTime now = LocalDateTime.now();
        AiTestopsDocumentParseResult parseResult = new AiTestopsDocumentParseResult();
        parseResult.setParseResultId(IdGenerator.parseResultId());
        parseResult.setDocumentId(documentId);
        parseResult.setMetadataJson(metadataJson);
        parseResult.setSectionsJson(sectionsJson);
        parseResult.setChunksJson(chunksJson);
        parseResult.setParseResultJson(parseResultJson);
        parseResult.setRawTextLength(rawTextLength);
        parseResult.setChunkCount(chunkCount);
        parseResult.setCreatedAt(now);
        parseResult.setUpdatedAt(now);
        parseResultService.save(parseResult);
        log.info("document_parse_result 保存完成: documentId={}, parseResultId={}", documentId, parseResult.getParseResultId());
    }

    private void updateParseSuccess(String documentId, String cleanedText, String metadataJson) {
        update(new LambdaUpdateWrapper<AiTestopsDocument>()
                .eq(AiTestopsDocument::getDocumentId, documentId)
                .set(AiTestopsDocument::getRawText, cleanedText)
                .set(AiTestopsDocument::getMetadataJson, metadataJson)
                .set(AiTestopsDocument::getParseStatus, ParseStatusEnum.SUCCESS.name())
                .set(AiTestopsDocument::getParseError, null)
                .set(AiTestopsDocument::getParsedAt, LocalDateTime.now())
                .set(AiTestopsDocument::getUpdatedAt, LocalDateTime.now()));
    }

    private void updateParseStatus(String documentId, ParseStatusEnum status, String parseError, LocalDateTime parsedAt) {
        LambdaUpdateWrapper<AiTestopsDocument> wrapper = new LambdaUpdateWrapper<AiTestopsDocument>()
                .eq(AiTestopsDocument::getDocumentId, documentId)
                .set(AiTestopsDocument::getParseStatus, status.name())
                .set(AiTestopsDocument::getParseError, parseError)
                .set(AiTestopsDocument::getUpdatedAt, LocalDateTime.now());
        if (parsedAt != null) {
            wrapper.set(AiTestopsDocument::getParsedAt, parsedAt);
        }
        update(wrapper);
        log.info("document parse_status 更新: documentId={}, parseStatus={}", documentId, status);
    }

    private AiTestopsDocument getRequiredDocument(String documentId) {
        AiTestopsDocument document = getOne(new LambdaQueryWrapper<AiTestopsDocument>()
                .eq(AiTestopsDocument::getDocumentId, documentId)
                .last("limit 1"), false);
        if (document == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND, "文档不存在: " + documentId);
        }
        return document;
    }

    private Map<String, Object> buildParseResult(AiTestopsDocument document, String cleanedText, Map<String, Object> metadata,
                                                 List<DocumentSection> sections, List<ChunkData> chunks) {
        Map<String, Object> result = new HashMap<>();
        result.put("documentId", document.getDocumentId());
        result.put("fileName", document.getFileName());
        result.put("fileType", document.getFileType());
        result.put("sourceType", document.getSourceType());
        result.put("rawTextLength", cleanedText.length());
        result.put("metadata", metadata);
        result.put("sections", sections);
        result.put("chunks", chunks);
        result.put("summary", summarize(cleanedText));
        return result;
    }

    private DocumentVO toDocumentVO(AiTestopsDocument document) {
        DocumentVO vo = new DocumentVO();
        vo.setDocumentId(document.getDocumentId());
        vo.setTitle(document.getTitle());
        vo.setSourceType(document.getSourceType());
        vo.setFileName(document.getFileName());
        vo.setFileType(document.getFileType());
        vo.setFileSize(document.getFileSize());
        vo.setFileHash(document.getFileHash());
        vo.setDuplicateDocumentId(document.getDuplicateDocumentId());
        vo.setParseStatus(document.getParseStatus());
        vo.setParseError(document.getParseError());
        vo.setRawTextSummary(summarize(document.getRawText()));
        vo.setRawTextLength(document.getRawText() == null ? 0 : document.getRawText().length());
        vo.setMetadataJson(document.getMetadataJson());
        vo.setUploadedAt(document.getUploadedAt());
        vo.setParsedAt(document.getParsedAt());
        vo.setCreatedAt(document.getCreatedAt());
        vo.setUpdatedAt(document.getUpdatedAt());
        return vo;
    }

    private DocumentChunkVO toChunkVO(AiTestopsDocumentChunk chunk) {
        DocumentChunkVO vo = new DocumentChunkVO();
        vo.setChunkId(chunk.getChunkId());
        vo.setDocumentId(chunk.getDocumentId());
        vo.setChunkIndex(chunk.getChunkIndex());
        vo.setSectionTitle(chunk.getSectionTitle());
        vo.setChunkText(chunk.getChunkText());
        vo.setTokenCount(chunk.getTokenCount());
        vo.setExtraJson(chunk.getExtraJson());
        vo.setCreatedAt(chunk.getCreatedAt());
        return vo;
    }

    private String summarize(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return truncate(normalized, SUMMARY_LENGTH);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
