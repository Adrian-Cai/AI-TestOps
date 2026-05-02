package com.example.aitestops.testcase.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.aitestops.testcase.entity.AiTestopsRequirementCaseMapping;
import com.example.aitestops.testcase.mapper.AiTestopsRequirementCaseMappingMapper;
import com.example.aitestops.testcase.service.AiTestopsRequirementCaseMappingService;
import org.springframework.stereotype.Service;

/**
 * 需求与测试用例映射服务实现。
 */
@Service
public class AiTestopsRequirementCaseMappingServiceImpl
        extends ServiceImpl<AiTestopsRequirementCaseMappingMapper, AiTestopsRequirementCaseMapping>
        implements AiTestopsRequirementCaseMappingService {
}
