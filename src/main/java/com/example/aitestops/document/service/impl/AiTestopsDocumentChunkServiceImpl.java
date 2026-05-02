package com.example.aitestops.document.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.aitestops.document.entity.AiTestopsDocumentChunk;
import com.example.aitestops.document.mapper.AiTestopsDocumentChunkMapper;
import com.example.aitestops.document.service.AiTestopsDocumentChunkService;
import org.springframework.stereotype.Service;

/**
 * 文档分块数据服务实现。
 */
@Service
public class AiTestopsDocumentChunkServiceImpl
        extends ServiceImpl<AiTestopsDocumentChunkMapper, AiTestopsDocumentChunk>
        implements AiTestopsDocumentChunkService {
}
