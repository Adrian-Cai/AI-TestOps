package com.example.aitestops.document.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.aitestops.document.dto.TextDocumentCreateRequest;
import com.example.aitestops.document.entity.AiTestopsDocument;
import com.example.aitestops.document.vo.DocumentChunkVO;
import com.example.aitestops.document.vo.DocumentParseSummaryVO;
import com.example.aitestops.document.vo.DocumentVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档业务服务，负责建档、上传、解析和查询。
 */
public interface AiTestopsDocumentService extends IService<AiTestopsDocument> {

    DocumentVO createTextDocument(TextDocumentCreateRequest request);

    DocumentVO uploadDocument(MultipartFile file, String title);

    DocumentParseSummaryVO parseDocument(String documentId);

    DocumentVO getDocumentDetail(String documentId);

    List<DocumentChunkVO> listDocumentChunks(String documentId);
}
