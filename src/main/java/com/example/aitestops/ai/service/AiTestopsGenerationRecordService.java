package com.example.aitestops.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.aitestops.ai.entity.AiTestopsGenerationRecord;
import com.example.aitestops.ai.vo.GenerationRecordVO;

/**
 * 大模型生成记录服务。
 */
public interface AiTestopsGenerationRecordService extends IService<AiTestopsGenerationRecord> {

    GenerationRecordVO getGenerationRecord(String generationId);
}
