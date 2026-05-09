package com.example.aitestops.diff.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.aitestops.diff.entity.AiTestopsDiffRiskActionRecord;
import com.example.aitestops.diff.mapper.AiTestopsDiffRiskActionRecordMapper;
import com.example.aitestops.diff.service.AiTestopsDiffRiskActionRecordService;
import org.springframework.stereotype.Service;

@Service
public class AiTestopsDiffRiskActionRecordServiceImpl
        extends ServiceImpl<AiTestopsDiffRiskActionRecordMapper, AiTestopsDiffRiskActionRecord>
        implements AiTestopsDiffRiskActionRecordService {
}
