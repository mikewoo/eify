package com.eify.provider.adapter.impl;

import com.eify.common.http.LlmHttpClient;
import com.eify.provider.constant.ProviderType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * OpenAI 兼容适配器
 * 与 OpenAI 使用相同的 API 规范，直接继承 OpenAiAdapter
 */
@Component
public class OpenAiCompatibleAdapter extends OpenAiAdapter {

    public OpenAiCompatibleAdapter(LlmHttpClient llmHttpClient, WebClient.Builder webClientBuilder) {
        super(llmHttpClient, webClientBuilder);
    }

    @Override
    public ProviderType getSupportedType() {
        return ProviderType.OPENAI_COMPATIBLE;
    }
}
