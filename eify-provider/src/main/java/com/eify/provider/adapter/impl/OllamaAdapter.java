package com.eify.provider.adapter.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.eify.common.http.LlmHttpClient;
import com.eify.provider.adapter.AbstractProviderAdapter;
import com.eify.provider.constant.ProviderType;
import com.eify.provider.domain.dto.ChatRequest;
import com.eify.provider.domain.dto.ChatResponse;
import com.eify.provider.domain.dto.ChatStreamChunk;
import com.eify.provider.domain.entity.Provider;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import reactor.core.publisher.Flux;

/**
 * Ollama 适配器（待实现）
 */
@Component
public class OllamaAdapter extends AbstractProviderAdapter {

    private final WebClient webClient;

    public OllamaAdapter(LlmHttpClient llmHttpClient, WebClient.Builder webClientBuilder) {
        super(llmHttpClient);
        this.webClient = webClientBuilder.build();
    }

    @Override
    public ProviderType getSupportedType() {
        return ProviderType.OLLAMA;
    }

    @Override
    public String getEmbeddingEndpoint() {
        return "/api/embeddings";
    }

    @Override
    protected String doTest(Provider provider) throws Exception {
        String baseUrl = normalizeBaseUrl(provider.getBaseUrl());

        // 移除 /v1 后缀（如果存在）
        if (baseUrl.endsWith("/v1")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 3);
        }

        String url = baseUrl + "/api/tags";

        // Ollama 默认不需要认证
        return llmHttpClient.get(url, null, TEST_TIMEOUT_SECONDS);
    }

    @Override
    protected int extractModelCount(String responseBody) {
        try {
            JSONObject json = JSON.parseObject(responseBody);
            JSONArray models = json.getJSONArray("models");
            return models != null ? models.size() : 0;
        } catch (Exception e) {
            log.warn("[Ollama] 解析模型数量失败: {}", e.getMessage());
            return 0;
        }
    }

    @Override
    protected java.util.List<String> extractModelNames(String responseBody) {
        try {
            JSONObject json = JSON.parseObject(responseBody);
            JSONArray models = json.getJSONArray("models");
            if (models == null) return java.util.Collections.emptyList();
            java.util.List<String> names = new java.util.ArrayList<>();
            for (int i = 0; i < models.size(); i++) {
                JSONObject item = models.getJSONObject(i);
                if (item != null && item.containsKey("name")) {
                    names.add(item.getString("name"));
                }
            }
            return names;
        } catch (Exception e) {
            log.warn("[Ollama] 解析模型名称失败: {}", e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    @Override
    protected String buildErrorMessage(Exception e, Provider provider) {
        String baseMsg = super.buildErrorMessage(e, provider);

        // Ollama 特定的友好错误提示
        if (baseMsg.contains("Connection refused")) {
            return "Ollama 服务未启动，请先运行 Ollama 服务";
        }
        if (baseMsg.contains("timeout") || baseMsg.contains("Timeout")) {
            return "Ollama 服务连接超时，请检查 Ollama 是否正常运行";
        }
        return "Ollama 服务连接失败，请检查服务状态";
    }

    @Override
    protected ChatResponse doChat(Provider provider, ChatRequest request) {
        throw new BusinessException(ErrorCode.MODEL_NOT_SUPPORTED);
    }

    @Override
    protected Flux<ChatStreamChunk> doStreamChat(Provider provider, ChatRequest request) {
        return Flux.error(new BusinessException(ErrorCode.MODEL_NOT_SUPPORTED));
    }
}
