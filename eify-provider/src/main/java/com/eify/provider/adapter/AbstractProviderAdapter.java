package com.eify.provider.adapter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import tools.jackson.databind.ObjectMapper;
import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import com.eify.common.http.LlmApiException;
import com.eify.common.http.LlmHttpClient;
import com.eify.provider.constant.ProviderType;
import com.eify.provider.domain.dto.ChatRequest;
import com.eify.provider.domain.dto.ChatResponse;
import com.eify.provider.domain.dto.ChatStreamChunk;
import com.eify.provider.domain.dto.ConnectionTestResult;
import com.eify.provider.domain.entity.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 适配器抽象基类
 * 提供通用功能，子类只需实现特定逻辑
 */
public abstract class AbstractProviderAdapter implements ProviderAdapter {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final LlmHttpClient llmHttpClient;
    protected final ObjectMapper objectMapper = new ObjectMapper();

    protected static final int TEST_TIMEOUT_SECONDS = 10;

    private ProviderCircuitBreakerManager circuitBreakerManager;

    public AbstractProviderAdapter(LlmHttpClient llmHttpClient) {
        this.llmHttpClient = llmHttpClient;
    }

    @Autowired
    public void setCircuitBreakerManager(ProviderCircuitBreakerManager circuitBreakerManager) {
        this.circuitBreakerManager = circuitBreakerManager;
    }

    @Override
    public ConnectionTestResult testConnection(Provider provider) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("[连通性测试] 开始测试 - Provider: {}, Type: {}, BaseURL: {}",
                    provider.getName(), provider.getType(), provider.getBaseUrl());

            ConnectionTestResult result;
            if (circuitBreakerManager != null) {
                result = circuitBreakerManager.executeTestConnection(provider, () -> {
                    try {
                        String responseBody = doTest(provider);
                        long latencyMs = System.currentTimeMillis() - startTime;
                        int modelCount = extractModelCount(responseBody);
                        List<String> modelNames = extractModelNames(responseBody);
                        return ConnectionTestResult.success(latencyMs, modelCount, modelNames);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            } else {
                String responseBody = doTest(provider);
                long latencyMs = System.currentTimeMillis() - startTime;
                int modelCount = extractModelCount(responseBody);
                List<String> modelNames = extractModelNames(responseBody);
                result = ConnectionTestResult.success(latencyMs, modelCount, modelNames);
            }

            log.info("[连通性测试] 成功 - Provider: {}, 延迟: {}ms, 模型数: {}",
                    provider.getName(), result.getLatencyMs(), result.getModelCount());

            return result;

        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            String errorMsg = buildErrorMessage(e, provider);
            log.error("[连通性测试] 失败 - Provider: {}, 延迟: {}ms, 错误: {}",
                    provider.getName(), latencyMs, errorMsg);
            return ConnectionTestResult.failure(latencyMs, errorMsg);
        }
    }

    /**
     * 执行测试（子类实现）
     */
    protected abstract String doTest(Provider provider) throws Exception;

    /**
     * 从响应中提取模型数量（子类可以覆盖）
     */
    protected int extractModelCount(String responseBody) {
        try {
            com.alibaba.fastjson2.JSONObject json = JSON.parseObject(responseBody);
            JSONArray data = json.getJSONArray("data");
            return data != null ? data.size() : 0;
        } catch (Exception e) {
            log.warn("[{}] 解析模型数量失败: {}", getSupportedType(), e.getMessage());
            return 0;
        }
    }

    /**
     * 从响应中提取模型名称列表（子类可以覆盖）
     * <p>
     * 默认解析 OpenAI 格式：{@code {"data": [{"id": "gpt-4o"}, ...]}}
     */
    protected List<String> extractModelNames(String responseBody) {
        try {
            com.alibaba.fastjson2.JSONObject json = JSON.parseObject(responseBody);
            JSONArray data = json.getJSONArray("data");
            if (data == null) return Collections.emptyList();
            List<String> names = new ArrayList<>();
            for (int i = 0; i < data.size(); i++) {
                com.alibaba.fastjson2.JSONObject item = data.getJSONObject(i);
                if (item != null && item.containsKey("id")) {
                    names.add(item.getString("id"));
                }
            }
            return names;
        } catch (Exception e) {
            log.warn("[{}] 解析模型名称失败: {}", getSupportedType(), e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 构建错误信息
     */
    protected String buildErrorMessage(Exception e, Provider provider) {
        if (e instanceof LlmApiException llmEx) {
            // 根据 Provider 类型提供更友好的错误提示
            String baseMsg = llmEx.getFullMessage();
            if (getSupportedType() == ProviderType.OLLAMA) {
                if (llmEx.getErrorType() == LlmApiException.ErrorType.TIMEOUT ||
                    llmEx.getErrorType() == LlmApiException.ErrorType.NETWORK_ERROR) {
                    if (baseMsg.contains("Connection refused")) {
                        return "Ollama 服务未启动，请先运行 Ollama 服务";
                    }
                    return "Ollama 服务连接失败，请检查 Ollama 是否正常运行";
                }
            }
            return baseMsg;
        }
        if (e instanceof BusinessException) {
            return e.getMessage();
        }
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    /**
     * 从 authConfig 提取 API Key
     */
    protected String getApiKey(Provider provider) {
        if (provider.getAuthConfig() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "认证配置缺失");
        }

        String apiKey = null;
        if (provider.getAuthConfig().has("api_key")) {
            apiKey = provider.getAuthConfig().get("api_key").asText();
        } else if (provider.getAuthConfig().has("apiKey")) {
            apiKey = provider.getAuthConfig().get("apiKey").asText();
        }

        if (apiKey == null || apiKey.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "API Key 未配置");
        }
        return apiKey;
    }

    /**
     * 规范化 baseUrl：移除末尾斜杠
     */
    protected String normalizeBaseUrl(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    /**
     * 规范化 baseUrl 到 /v1 级别，去除 /v1 之后的路径段（如 /embeddings、/chat/completions 等），
     * 防止用户将具体 API 端点误填为 baseUrl 时出现重复路径。
     */
    protected String normalizeV1BaseUrl(String baseUrl) {
        baseUrl = normalizeBaseUrl(baseUrl);
        int v1SlashIndex = baseUrl.indexOf("/v1/");
        if (v1SlashIndex >= 0) {
            return baseUrl.substring(0, v1SlashIndex + 3);
        }
        if (!baseUrl.endsWith("/v1")) {
            baseUrl += "/v1";
        }
        return baseUrl;
    }

    /**
     * 构建 /v1/models 端点 URL
     */
    protected String buildModelsUrl(String baseUrl) {
        return normalizeV1BaseUrl(baseUrl) + "/models";
    }

    // ========== 对话方法 ==========

    @Override
    public ChatResponse chat(Provider provider, ChatRequest request) {
        long startTime = System.currentTimeMillis();

        logChatRequest(provider, request, false);
        validateRequest(request);

        try {
            ChatResponse response;
            if (circuitBreakerManager != null) {
                response = circuitBreakerManager.executeWithBreaker(provider, () -> {
                    long innerStart = System.currentTimeMillis();
                    ChatResponse r = doChat(provider, request);
                    long latency = System.currentTimeMillis() - innerStart;
                    logChatResponse(provider, r, latency, false);
                    return r;
                });
            } else {
                response = doChat(provider, request);
                long latency = System.currentTimeMillis() - startTime;
                logChatResponse(provider, response, latency, false);
            }

            return response;

        } catch (LlmApiException e) {
            logChatError(provider, e, false);
            throw e;
        } catch (Exception e) {
            logChatError(provider, e, false);
            throw e;
        }
    }

    @Override
    public Flux<ChatStreamChunk> streamChat(Provider provider, ChatRequest request) {
        logChatRequest(provider, request, true);
        validateRequest(request);

        Flux<ChatStreamChunk> stream = doStreamChat(provider, request)
                .doOnComplete(() -> log.debug("[流式对话] 完成 - Provider: {}", provider.getName()))
                .doOnError(error -> logChatError(provider, error, true));

        if (circuitBreakerManager != null) {
            return circuitBreakerManager.executeWithBreakerReactive(provider, () -> stream);
        }
        return stream;
    }

    /**
     * 执行同步对话（由子类实现）
     */
    protected abstract ChatResponse doChat(Provider provider, ChatRequest request);

    /**
     * 执行流式对话（由子类实现）
     */
    protected abstract Flux<ChatStreamChunk> doStreamChat(Provider provider, ChatRequest request);

    /**
     * 参数校验
     */
    protected void validateRequest(ChatRequest request) {
        if (request.getModel() == null || request.getModel().isBlank()) {
            throw new IllegalArgumentException("model 不能为空");
        }
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new IllegalArgumentException("messages 不能为空");
        }
        if (request.getTemperature() != null &&
                (request.getTemperature() < 0 || request.getTemperature() > 2)) {
            throw new IllegalArgumentException("temperature 必须在 [0, 2] 范围内");
        }
        if (request.getTopP() != null &&
                (request.getTopP() < 0 || request.getTopP() > 1)) {
            throw new IllegalArgumentException("topP 必须在 [0, 1] 范围内");
        }
    }

    /**
     * 记录对话请求日志
     */
    protected void logChatRequest(Provider provider, ChatRequest request, boolean stream) {
        log.info("[{}] 发起{}对话 - Provider: {}, Model: {}, 消息数: {}, MaxTokens: {}, Temperature: {}",
                getSupportedType(),
                stream ? "流式" : "同步",
                provider.getName(),
                request.getModel(),
                request.getMessages().size(),
                request.getMaxTokens(),
                request.getTemperature());
    }

    /**
     * 记录对话响应日志
     */
    protected void logChatResponse(Provider provider, ChatResponse response, long latency, boolean stream) {
        if (response.getUsage() != null) {
            log.info("[{}] {}对话完成 - Provider: {}, 延迟: {}ms, PromptTokens: {}, CompletionTokens: {}, TotalTokens: {}",
                    getSupportedType(),
                    stream ? "流式" : "同步",
                    provider.getName(),
                    latency,
                    response.getUsage().getPromptTokens(),
                    response.getUsage().getCompletionTokens(),
                    response.getUsage().getTotalTokens());
        } else {
            log.info("[{}] {}对话完成 - Provider: {}, 延迟: {}ms",
                    getSupportedType(),
                    stream ? "流式" : "同步",
                    provider.getName(),
                    latency);
        }
    }

    /**
     * 记录对话错误日志
     */
    protected void logChatError(Provider provider, Throwable error, boolean stream) {
        log.error("[{}] {}对话失败 - Provider: {}, 错误: {}",
                getSupportedType(),
                stream ? "流式" : "同步",
                provider.getName(),
                error.getMessage());
    }
}
