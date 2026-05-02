package com.example.aitestops.document;

import com.example.aitestops.common.enums.ParseStatusEnum;
import com.example.aitestops.common.enums.SourceTypeEnum;
import com.example.aitestops.common.exception.BusinessException;
import com.example.aitestops.document.dto.TextDocumentCreateRequest;
import com.example.aitestops.document.service.AiTestopsDocumentService;
import com.example.aitestops.document.vo.DocumentParseSummaryVO;
import com.example.aitestops.document.vo.DocumentVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AiTestopsDocumentServiceIntegrationTest {

    @Autowired
    private AiTestopsDocumentService documentService;

    @Test
    void createTextDocumentAndParseShouldPersistChunks() {
        TextDocumentCreateRequest request = new TextDocumentCreateRequest();
        request.setTitle("订单需求");
        request.setContent("用户可以提交订单。\n\n库存不足时提交失败。");

        DocumentVO created = documentService.createTextDocument(request);
        DocumentParseSummaryVO parsed = documentService.parseDocument(created.getDocumentId());

        assertThat(created.getSourceType()).isEqualTo(SourceTypeEnum.TEXT.name());
        assertThat(parsed.getParseStatus()).isEqualTo(ParseStatusEnum.SUCCESS.name());
        assertThat(documentService.listDocumentChunks(created.getDocumentId())).isNotEmpty();
    }

    @Test
    void uploadDocumentShouldRejectUnsupportedFileType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "bad.exe",
                "application/octet-stream",
                "content".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> documentService.uploadDocument(file, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件类型不支持");
    }

    @Test
    void getDocumentDetailShouldRejectMissingDocument() {
        assertThatThrownBy(() -> documentService.getDocumentDetail("DOC_NOT_EXIST"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文档不存在");
    }
}
