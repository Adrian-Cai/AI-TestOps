package com.example.aitestops.document.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.aitestops.document.entity.AiTestopsDocumentParseResult;
import com.example.aitestops.document.mapper.AiTestopsDocumentParseResultMapper;
import com.example.aitestops.document.service.AiTestopsDocumentParseResultService;
import org.springframework.stereotype.Service;

/**
 * 文档解析结果数据服务实现。
 */
@Service
public class AiTestopsDocumentParseResultServiceImpl
        extends ServiceImpl<AiTestopsDocumentParseResultMapper, AiTestopsDocumentParseResult>
        implements AiTestopsDocumentParseResultService {
}
