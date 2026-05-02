package com.example.aitestops.ai.client;

import com.example.aitestops.ai.dto.AiChatRequest;
import com.example.aitestops.ai.dto.AiChatResponse;

/**
 * 通用 AI Client 接口，后续可替换 OpenAI、Qwen、DeepSeek 等实现。
 */
public interface AiClient {

    AiChatResponse chat(AiChatRequest request);
}
