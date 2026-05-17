package com.example.aitestops.diff.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.aitestops.diff.entity.AiTestopsDiffRiskItem;
import com.example.aitestops.diff.mapper.AiTestopsDiffRiskItemMapper;
import com.example.aitestops.diff.service.AiTestopsDiffRiskItemService;
import org.springframework.stereotype.Service;

/**
 * Diff 风险项服务实现。
 */
@Service
public class AiTestopsDiffRiskItemServiceImpl
        extends ServiceImpl<AiTestopsDiffRiskItemMapper, AiTestopsDiffRiskItem>
        implements AiTestopsDiffRiskItemService {
}
