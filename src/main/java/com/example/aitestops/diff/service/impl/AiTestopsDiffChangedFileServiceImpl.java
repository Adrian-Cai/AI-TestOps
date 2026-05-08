package com.example.aitestops.diff.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.aitestops.diff.entity.AiTestopsDiffChangedFile;
import com.example.aitestops.diff.mapper.AiTestopsDiffChangedFileMapper;
import com.example.aitestops.diff.service.AiTestopsDiffChangedFileService;
import org.springframework.stereotype.Service;

@Service
public class AiTestopsDiffChangedFileServiceImpl
        extends ServiceImpl<AiTestopsDiffChangedFileMapper, AiTestopsDiffChangedFile>
        implements AiTestopsDiffChangedFileService {
}
