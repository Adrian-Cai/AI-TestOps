package com.example.aitestops.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.aitestops.ai.dto.RequirementExtractRequest;
import com.example.aitestops.ai.entity.AiTestopsRequirementExtract;
import com.example.aitestops.ai.vo.RequirementExtractVO;

/**
 * AI 需求解析服务。
 */
public interface AiTestopsRequirementExtractService extends IService<AiTestopsRequirementExtract> {

    RequirementExtractVO extractRequirements(RequirementExtractRequest request);
}
