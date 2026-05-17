package com.example.aitestops.diff.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.aitestops.diff.entity.AiTestopsDiffMergeGateReport;
import com.example.aitestops.diff.mapper.AiTestopsDiffMergeGateReportMapper;
import com.example.aitestops.diff.service.AiTestopsDiffMergeGateReportService;
import org.springframework.stereotype.Service;

/**
 * Diff 合并准入报告服务实现。
 */
@Service
public class AiTestopsDiffMergeGateReportServiceImpl
        extends ServiceImpl<AiTestopsDiffMergeGateReportMapper, AiTestopsDiffMergeGateReport>
        implements AiTestopsDiffMergeGateReportService {
}
