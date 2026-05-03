package com.example.aitestops.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.aitestops.ai.entity.AiTestopsGenerationRecord;
import com.example.aitestops.ai.mapper.AiTestopsGenerationRecordMapper;
import com.example.aitestops.ai.service.AiTestopsGenerationRecordService;
import com.example.aitestops.ai.vo.GenerationRecordVO;
import com.example.aitestops.common.exception.BusinessException;
import com.example.aitestops.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * 大模型生成记录服务实现。
 */
@Service
public class AiTestopsGenerationRecordServiceImpl
        extends ServiceImpl<AiTestopsGenerationRecordMapper, AiTestopsGenerationRecord>
        implements AiTestopsGenerationRecordService {

    @Override
    public GenerationRecordVO getGenerationRecord(String generationId) {
        AiTestopsGenerationRecord record = getOne(new LambdaQueryWrapper<AiTestopsGenerationRecord>()
                .eq(AiTestopsGenerationRecord::getGenerationId, generationId)
                .last("limit 1"), false);
        if (record == null) {
            throw new BusinessException(ErrorCode.GENERATION_NOT_FOUND, "生成记录不存在: " + generationId);
        }
        return toVO(record);
    }

    private GenerationRecordVO toVO(AiTestopsGenerationRecord record) {
        GenerationRecordVO vo = new GenerationRecordVO();
        vo.setGenerationId(record.getGenerationId());
        vo.setDocumentId(record.getDocumentId());
        vo.setRequirementExtractId(record.getRequirementExtractId());
        vo.setPromptTemplateCode(record.getPromptTemplateCode());
        vo.setPromptTemplateVersion(record.getPromptTemplateVersion());
        vo.setModelCode(record.getModelCode());
        vo.setModelName(record.getModelName());
        vo.setGenerationType(record.getGenerationType());
        vo.setInputSnapshotJson(record.getInputSnapshotJson());
        vo.setOutputJson(record.getOutputJson());
        vo.setStatus(record.getStatus());
        vo.setErrorMessage(record.getErrorMessage());
        vo.setTokenInput(record.getTokenInput());
        vo.setTokenOutput(record.getTokenOutput());
        vo.setStartedAt(record.getStartedAt());
        vo.setFinishedAt(record.getFinishedAt());
        vo.setCreatedAt(record.getCreatedAt());
        vo.setUpdatedAt(record.getUpdatedAt());
        return vo;
    }
}
