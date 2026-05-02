package com.example.aitestops.ai;

import com.example.aitestops.ai.dto.RequirementExtractRequest;
import com.example.aitestops.ai.service.AiTestopsGenerationRecordService;
import com.example.aitestops.ai.service.AiTestopsRequirementExtractService;
import com.example.aitestops.ai.vo.GenerationRecordVO;
import com.example.aitestops.ai.vo.RequirementExtractVO;
import com.example.aitestops.common.enums.GenerationStatusEnum;
import com.example.aitestops.document.dto.TextDocumentCreateRequest;
import com.example.aitestops.document.service.AiTestopsDocumentService;
import com.example.aitestops.document.vo.DocumentVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AiRequirementExtractServiceIntegrationTest {

    @Autowired
    private AiTestopsDocumentService documentService;

    @Autowired
    private AiTestopsRequirementExtractService requirementExtractService;

    @Autowired
    private AiTestopsGenerationRecordService generationRecordService;

    @Test
    void extractRequirementsShouldPersistGenerationAndRequirementResultInMockMode() {
        TextDocumentCreateRequest createRequest = new TextDocumentCreateRequest();
        createRequest.setTitle("订单需求");
        createRequest.setContent("用户可以提交订单。\n\n库存不足时提交失败。");
        DocumentVO document = documentService.createTextDocument(createRequest);
        documentService.parseDocument(document.getDocumentId());

        RequirementExtractRequest request = new RequirementExtractRequest();
        request.setDocumentId(document.getDocumentId());
        RequirementExtractVO extract = requirementExtractService.extractRequirements(request);
        GenerationRecordVO generation = generationRecordService.getGenerationRecord(extract.getGenerationId());

        assertThat(extract.getRequirementExtractId()).startsWith("REXT_");
        assertThat(extract.getRequirementsJson()).contains("REQ_001");
        assertThat(generation.getStatus()).isEqualTo(GenerationStatusEnum.SUCCESS.name());
        assertThat(generation.getOutputJson()).contains("requirements");
    }
}
