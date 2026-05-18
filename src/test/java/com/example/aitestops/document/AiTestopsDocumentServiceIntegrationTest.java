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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
    void uploadDocumentsWithSameFilenameButDifferentContentShouldUseDifferentHashes() {
        MockMultipartFile firstFile = markdownFile("same-name.md", "first content " + UUID.randomUUID());
        MockMultipartFile secondFile = markdownFile("same-name.md", "second content " + UUID.randomUUID());

        DocumentVO first = documentService.uploadDocument(firstFile, "same filename first");
        DocumentVO second = documentService.uploadDocument(secondFile, "same filename second");

        assertThat(second.getDocumentId()).isNotEqualTo(first.getDocumentId());
        assertThat(second.getFileHash()).isNotEqualTo(first.getFileHash());
        assertThat(second.getDuplicateDocumentId()).isNull();
    }

    @Test
    void uploadDocumentsWithDifferentFilenamesButSameContentShouldKeepNewDocumentAndMarkDuplicate() {
        String content = "duplicate content " + UUID.randomUUID();
        MockMultipartFile firstFile = markdownFile("first-file.md", content);
        MockMultipartFile secondFile = markdownFile("second-file.md", content);

        DocumentVO first = documentService.uploadDocument(firstFile, "first duplicate upload");
        DocumentVO second = documentService.uploadDocument(secondFile, "second duplicate upload");

        assertThat(second.getDocumentId()).isNotEqualTo(first.getDocumentId());
        assertThat(second.getFileHash()).isEqualTo(first.getFileHash());
        assertThat(second.getDuplicateDocumentId()).isEqualTo(first.getDocumentId());
    }

    @Test
    void concurrentUploadsShouldCreateUniqueDocumentsWithoutCollisions() throws Exception {
        int uploadCount = 80;
        String sharedContent = "shared concurrent content " + UUID.randomUUID();
        ExecutorService executorService = Executors.newFixedThreadPool(12);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<DocumentVO>> futures = new ArrayList<>();

        try {
            IntStream.range(0, uploadCount).forEach(index -> futures.add(executorService.submit(() -> {
                start.await();
                String content = index % 2 == 0
                        ? sharedContent
                        : "polling content " + index + " " + UUID.randomUUID();
                return documentService.uploadDocument(markdownFile("concurrent-" + index + ".md", content),
                        "concurrent upload " + index);
            })));

            start.countDown();

            List<DocumentVO> documents = new ArrayList<>();
            for (Future<DocumentVO> future : futures) {
                documents.add(future.get(30, TimeUnit.SECONDS));
            }

            Set<String> documentIds = documents.stream()
                    .map(DocumentVO::getDocumentId)
                    .collect(Collectors.toSet());
            assertThat(documentIds).hasSize(uploadCount);
            assertThat(documents).allSatisfy(document -> assertThat(document.getDocumentId()).startsWith("DOC_"));
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void getDocumentDetailShouldRejectMissingDocument() {
        assertThatThrownBy(() -> documentService.getDocumentDetail("DOC_NOT_EXIST"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文档不存在");
    }

    private MockMultipartFile markdownFile(String filename, String content) {
        return new MockMultipartFile(
                "file",
                filename,
                "text/markdown",
                content.getBytes(StandardCharsets.UTF_8)
        );
    }
}
