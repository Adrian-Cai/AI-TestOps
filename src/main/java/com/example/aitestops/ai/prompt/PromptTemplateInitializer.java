package com.example.aitestops.ai.prompt;

import com.example.aitestops.ai.service.AiTestopsPromptTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Prompt 模板初始化器，应用启动时写入默认模板。
 */
@Component
@RequiredArgsConstructor
public class PromptTemplateInitializer implements ApplicationRunner {

    private final AiTestopsPromptTemplateService promptTemplateService;

    @Override
    public void run(ApplicationArguments args) {
        promptTemplateService.initDefaultTemplates();
    }
}
