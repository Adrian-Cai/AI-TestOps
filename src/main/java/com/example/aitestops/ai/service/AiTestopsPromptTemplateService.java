package com.example.aitestops.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.aitestops.ai.dto.PromptTemplateVersionCreateRequest;
import com.example.aitestops.ai.entity.AiTestopsPromptTemplate;
import com.example.aitestops.ai.vo.PromptQualityVO;
import com.example.aitestops.ai.vo.PromptTemplateVO;

import java.util.List;

/**
 * Prompt 模板服务。
 */
public interface AiTestopsPromptTemplateService extends IService<AiTestopsPromptTemplate> {

    AiTestopsPromptTemplate getEnabledTemplate(String templateCode);

    List<PromptTemplateVO> listVersions(String templateCode, String templateType);

    PromptTemplateVO createVersion(PromptTemplateVersionCreateRequest request);

    PromptTemplateVO activateVersion(String templateCode, String version);

    List<PromptQualityVO> listQuality(String templateCode, String generationType);

    void initDefaultTemplates();
}
