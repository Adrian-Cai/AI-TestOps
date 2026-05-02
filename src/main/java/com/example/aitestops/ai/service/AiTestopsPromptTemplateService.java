package com.example.aitestops.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.aitestops.ai.entity.AiTestopsPromptTemplate;

/**
 * Prompt 模板服务。
 */
public interface AiTestopsPromptTemplateService extends IService<AiTestopsPromptTemplate> {

    AiTestopsPromptTemplate getEnabledTemplate(String templateCode);

    void initDefaultTemplates();
}
