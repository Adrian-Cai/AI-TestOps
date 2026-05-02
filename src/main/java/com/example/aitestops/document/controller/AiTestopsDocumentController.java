package com.example.aitestops.document.controller;

import com.example.aitestops.common.response.ApiResponse;
import com.example.aitestops.document.dto.TextDocumentCreateRequest;
import com.example.aitestops.document.service.AiTestopsDocumentService;
import com.example.aitestops.document.vo.DocumentChunkVO;
import com.example.aitestops.document.vo.DocumentParseSummaryVO;
import com.example.aitestops.document.vo.DocumentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档模块 REST API，只负责参数接收和响应返回。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai-testops/documents")
@Tag(name = "AI测试设计-文档模块")
public class AiTestopsDocumentController {

    private final AiTestopsDocumentService documentService;

    @PostMapping("/text")
    @Operation(summary = "提交需求文本，创建 document 记录")
    public ApiResponse<DocumentVO> createTextDocument(@Valid @RequestBody TextDocumentCreateRequest request) {
        return ApiResponse.success(documentService.createTextDocument(request));
    }

    @PostMapping("/upload")
    @Operation(summary = "上传文件，保存原始文件并创建 document 记录")
    public ApiResponse<DocumentVO> uploadDocument(@RequestParam("file") MultipartFile file,
                                                  @RequestParam(value = "title", required = false) String title) {
        return ApiResponse.success(documentService.uploadDocument(file, title));
    }

    @PostMapping("/{documentId}/parse")
    @Operation(summary = "解析文档，保存 raw_text、metadata、chunks 和 parse_result")
    public ApiResponse<DocumentParseSummaryVO> parseDocument(@PathVariable String documentId) {
        return ApiResponse.success(documentService.parseDocument(documentId));
    }

    @GetMapping("/{documentId}")
    @Operation(summary = "查询文档详情")
    public ApiResponse<DocumentVO> getDocumentDetail(@PathVariable String documentId) {
        return ApiResponse.success(documentService.getDocumentDetail(documentId));
    }

    @GetMapping("/{documentId}/chunks")
    @Operation(summary = "查询文档分块列表")
    public ApiResponse<List<DocumentChunkVO>> listDocumentChunks(@PathVariable String documentId) {
        return ApiResponse.success(documentService.listDocumentChunks(documentId));
    }
}
