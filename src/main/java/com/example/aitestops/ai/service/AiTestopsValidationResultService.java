package com.example.aitestops.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.aitestops.ai.entity.AiTestopsValidationResult;
import com.example.aitestops.ai.vo.ValidationResultVO;

import java.util.List;

/**
 * AI 输出校验结果服务。
 */
public interface AiTestopsValidationResultService extends IService<AiTestopsValidationResult> {

    List<ValidationResultVO> listByGenerationId(String generationId);
}
