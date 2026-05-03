package com.example.aitestops.ai;

import com.example.aitestops.ai.dto.PromptTemplateVersionCreateRequest;
import com.example.aitestops.ai.entity.AiTestopsPromptTemplate;
import com.example.aitestops.ai.service.AiTestopsPromptTemplateService;
import com.example.aitestops.ai.vo.PromptQualityVO;
import com.example.aitestops.ai.vo.PromptTemplateVO;
import com.example.aitestops.common.enums.GenerationTypeEnum;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AiPromptTemplateServiceIntegrationTest {

    @Autowired
    private AiTestopsPromptTemplateService promptTemplateService;

    @Test
    void createAndActivateVersionShouldKeepOneEnabledTemplatePerCode() {
        PromptTemplateVersionCreateRequest request = new PromptTemplateVersionCreateRequest();
        request.setTemplateCode("TEST_CASE_GENERATE");
        request.setTemplateName("测试用例生成模板 v9");
        request.setTemplateType(GenerationTypeEnum.TEST_CASE_GENERATE.name());
        request.setVersion("v9.9.0");
        request.setPromptContent("只能输出 JSON。");
        request.setJsonSchema("{\"type\":\"object\",\"required\":[\"test_cases\"]}");
        request.setEnabled(false);

        PromptTemplateVO created = promptTemplateService.createVersion(request);
        assertThat(created.getEnabled()).isZero();

        PromptTemplateVO activated = promptTemplateService.activateVersion("TEST_CASE_GENERATE", "v9.9.0");
        AiTestopsPromptTemplate enabled = promptTemplateService.getEnabledTemplate("TEST_CASE_GENERATE");
        List<PromptTemplateVO> versions = promptTemplateService.listVersions("TEST_CASE_GENERATE", null);
        List<PromptQualityVO> quality = promptTemplateService.listQuality("TEST_CASE_GENERATE",
                GenerationTypeEnum.TEST_CASE_GENERATE.name());

        assertThat(activated.getEnabled()).isEqualTo(1);
        assertThat(enabled.getVersion()).isEqualTo("v9.9.0");
        assertThat(versions).filteredOn(item -> item.getEnabled() == 1).hasSize(1);
        assertThat(quality).extracting(PromptQualityVO::getVersion).contains("v9.9.0");
    }
}
