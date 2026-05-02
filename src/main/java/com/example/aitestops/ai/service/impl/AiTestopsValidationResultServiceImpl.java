package com.example.aitestops.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.aitestops.ai.entity.AiTestopsValidationResult;
import com.example.aitestops.ai.mapper.AiTestopsValidationResultMapper;
import com.example.aitestops.ai.service.AiTestopsValidationResultService;
import com.example.aitestops.ai.vo.ValidationResultVO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 输出校验结果服务实现。
 */
@Service
public class AiTestopsValidationResultServiceImpl
        extends ServiceImpl<AiTestopsValidationResultMapper, AiTestopsValidationResult>
        implements AiTestopsValidationResultService {

    @Override
    public List<ValidationResultVO> listByGenerationId(String generationId) {
        return list(new LambdaQueryWrapper<AiTestopsValidationResult>()
                .eq(AiTestopsValidationResult::getGenerationId, generationId)
                .orderByAsc(AiTestopsValidationResult::getId))
                .stream()
                .map(this::toVO)
                .toList();
    }

    private ValidationResultVO toVO(AiTestopsValidationResult result) {
        ValidationResultVO vo = new ValidationResultVO();
        vo.setValidationId(result.getValidationId());
        vo.setGenerationId(result.getGenerationId());
        vo.setValidationType(result.getValidationType());
        vo.setStatus(result.getStatus());
        vo.setErrorDetailJson(result.getErrorDetailJson());
        vo.setWarningDetailJson(result.getWarningDetailJson());
        vo.setCreatedAt(result.getCreatedAt());
        return vo;
    }
}
