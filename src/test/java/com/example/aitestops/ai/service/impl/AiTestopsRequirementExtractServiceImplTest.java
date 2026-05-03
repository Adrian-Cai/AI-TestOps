package com.example.aitestops.ai.service.impl;

import com.example.aitestops.ai.dto.AiChatResponse;
import com.example.aitestops.ai.dto.RequirementExtractRequest;
import com.example.aitestops.testcase.dto.TestCaseGenerateRequest;
import com.example.aitestops.testcase.service.impl.AiTestopsTestCaseDraftServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class AiTestopsRequirementExtractServiceImplTest {

    private final AiTestopsRequirementExtractServiceImpl service = new AiTestopsRequirementExtractServiceImpl(
            null,
            null,
            null,
            null,
            null,
            null,
            new ObjectMapper()
    );

    @Test
    void parseAndValidateRequirementJsonShouldNormalizeMissingOptionalArrays() {
        AiChatResponse response = AiChatResponse.builder()
                .content("""
                        {
                          "requirements": [
                            {
                              "requirement_id": "REQ_001",
                              "title": "登录",
                              "content": "用户可以登录",
                              "priority": "P1",
                              "risk_level": "MEDIUM",
                              "source_chunks": ["CHUNK_001"]
                            }
                          ]
                        }
                        """)
                .finishReason("stop")
                .build();

        JsonNode root = service.parseAndValidateRequirementJson(response, "GEN_TEST");

        assertThat(root.get("requirements")).hasSize(1);
        assertThat(root.get("business_rules").isArray()).isTrue();
        assertThat(root.get("api_list").isArray()).isTrue();
        assertThat(root.get("field_constraints").isArray()).isTrue();
        assertThat(root.get("exception_cases").isArray()).isTrue();
        assertThat(root.get("risks").isArray()).isTrue();
    }

    @Test
    void aiGenerationEntryPointsShouldNotHoldDatabaseTransactionDuringModelCall() throws Exception {
        Method requirementExtract = AiTestopsRequirementExtractServiceImpl.class
                .getMethod("extractRequirements", RequirementExtractRequest.class);
        Method testCaseGenerate = AiTestopsTestCaseDraftServiceImpl.class
                .getMethod("generateDrafts", TestCaseGenerateRequest.class);

        assertThat(requirementExtract.isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(testCaseGenerate.isAnnotationPresent(Transactional.class)).isFalse();
    }
}
