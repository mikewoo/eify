package com.eify.provider.adapter.impl;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import com.eify.common.http.LlmHttpClient;
import com.eify.provider.adapter.AbstractProviderAdapter;
import com.eify.provider.constant.ProviderType;
import com.eify.provider.domain.dto.ChatMessage;
import com.eify.provider.domain.dto.ChatRequest;
import com.eify.provider.domain.dto.ChatResponse;
import com.eify.provider.domain.dto.ChatStreamChunk;
import com.eify.provider.domain.entity.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 适配器
 * <p>
 * 支持 OPENAI 和 OPENAI_COMPATIBLE 类型
 * 实现同步对话和流式对话
 */
@Component
public class OpenAiAdapter extends AbstractProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(OpenAiAdapter.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiAdapter(LlmHttpClient llmHttpClient, WebClient.Builder webClientBuilder) {
        super(llmHttpClient);
        this.webClient = webClientBuilder.build();
    }

    @Override
    public ProviderType getSupportedType() {
        return ProviderType.OPENAI;
    }

    @Override
    protected String doTest(Provider provider) throws Exception {
        String url = buildModelsUrl(provider.getBaseUrl());

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + getApiKey(provider));

        return llmHttpClient.get(url, headers, TEST_TIMEOUT_SECONDS);
    }

    @Override
    protected ChatResponse doChat(Provider provider, ChatRequest request) {
        try {
            // 构建 OpenAI API 请求体
            Map<String, Object> requestBody = buildOpenAiRequestBody(request);

            // 发起 HTTP 请求
            String url = buildChatUrl(provider);
            Map<String, String> headers = buildHeaders(provider);

            // 将请求体转换为 JSON 字符串
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            String response = llmHttpClient.post(url, headers, jsonBody);

            // 解析响应
            return parseChatResponse(response);

        } catch (Exception e) {
            log.error("[OpenAI] 同步对话失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.PROVIDER_CALL_FAILED);
        }
    }

    @Override
    protected Flux<ChatStreamChunk> doStreamChat(Provider provider, ChatRequest request) {
        String url = buildChatUrl(provider);
        Map<String, Object> requestBody = buildOpenAiRequestBody(request);
        requestBody.put("stream", true);  // 关键：启用流式
        requestBody.put("stream_options", Map.of("include_usage", true));

        long startTime = System.currentTimeMillis();
        log.info("[OpenAI] 发起流式对话 - URL: {}, Model: {}, Messages: {}",
                url, request.getModel(), request.getMessages().size());

        // ========== 使用 SSE 解码器处理流式响应 ==========
        java.util.concurrent.atomic.AtomicBoolean firstChunk = new java.util.concurrent.atomic.AtomicBoolean(true);
        Map<Integer, ChatStreamChunk.ToolCallChunk> toolCallAccumulator = new java.util.concurrent.ConcurrentHashMap<>();

        return webClient.post()
                .uri(url)
                .headers(h -> {
                    h.set("Authorization", "Bearer " + getApiKey(provider));
                    h.set("Content-Type", "application/json");
                    // 添加 keep-alive 以维持长连接
                    h.set("Connection", "keep-alive");
                })
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                // 将 DataBuffer 转换为字符串
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                })
                // 按行分割 SSE 数据
                .flatMap(text -> {
                    // SSE 格式: data: {...}\n\n 或 data: [DONE]\n\n
                    String[] lines = text.split("\\r?\\n\\r?\\n");
                    return Flux.fromArray(lines);
                })
                // 记录原始 SSE 数据
                .doOnNext(line -> {
                    if (line != null && !line.isEmpty()) {
                        log.debug("[OpenAI] SSE 原始数据: {}", line.substring(0, Math.min(200, line.length())));
                    }
                })
                // ========== SSE 行解析（含工具调用累积）==========
                .flatMap(line -> parseSseLine(line, toolCallAccumulator))
                .doOnNext(chunk -> {
                    // 记录首字节时间
                    if (firstChunk.compareAndSet(true, false)) {
                        long ttfb = System.currentTimeMillis() - startTime;
                        log.info("[OpenAI] 首字节时间(TTFB) - Model: {}, {}ms", request.getModel(), ttfb);
                    }
                })
                .doOnCancel(() -> {
                    log.warn("[OpenAI] 客户端断开连接 - Model: {}", request.getModel());
                })
                .doOnError(error -> {
                    log.error("[OpenAI] SSE 流式调用出错 - Model: {}, Error: {}, Type: {}",
                            request.getModel(),
                            error.getMessage(),
                            error.getClass().getSimpleName());
                })
                // 确保 DNS 解析等阻塞操作不在 Netty event loop 上执行
                .subscribeOn(Schedulers.boundedElastic())
                // 对连接错误进行重试（最多 3 次）
                .retryWhen(reactor.util.retry.Retry.max(3)
                        .filter(throwable -> {
                            // 只对网络相关错误进行重试
                            return throwable instanceof java.io.IOException ||
                                   throwable instanceof java.net.SocketException ||
                                   throwable.getMessage() != null &&
                                   (throwable.getMessage().contains("Connection reset") ||
                                    throwable.getMessage().contains("Connection refused") ||
                                    throwable.getMessage().contains("timeout"));
                        })
                        .doBeforeRetry(signal -> {
                            log.warn("[OpenAI] 连接失败，正在重试 (第 {} 次) - Model: {}, Error: {}",
                                    signal.totalRetries() + 1, request.getModel(),
                                    signal.failure().getMessage());
                        })
                );
    }

    /**
     * 解析 SSE 行（含工具调用累积）
     */
    private Mono<ChatStreamChunk> parseSseLine(String line, Map<Integer, ChatStreamChunk.ToolCallChunk> acc) {
        if (line == null || line.isEmpty()) {
            return Mono.empty();
        }

        if (line.startsWith("data: ")) {
            String json = line.substring(6);

            if ("[DONE]".equals(json)) {
                log.debug("[OpenAI] 接收到 [DONE] 标记，流结束");
                return Mono.empty();
            }

            try {
                JsonNode root = objectMapper.readTree(json);
                JsonNode choices = root.path("choices");
                if (choices.isArray() && choices.size() > 0) {
                    JsonNode choice = choices.get(0);

                    // 检查 finish_reason
                    String finishReason = null;
                    if (choice.has("finish_reason") && !choice.get("finish_reason").isNull()) {
                        finishReason = choice.get("finish_reason").asText();
                    }

                    JsonNode delta = choice.path("delta");

                    // --- 处理工具调用 ---
                    JsonNode toolCallsNode = delta.path("tool_calls");
                    if (toolCallsNode.isArray() && toolCallsNode.size() > 0) {
                        for (JsonNode tcNode : toolCallsNode) {
                            int idx = tcNode.path("index").asInt(-1);
                            if (idx < 0) continue;
                            ChatStreamChunk.ToolCallChunk tc = acc.computeIfAbsent(idx,
                                    k -> ChatStreamChunk.ToolCallChunk.builder().index(k).arguments("").build());
                            if (tcNode.has("id") && !tcNode.get("id").isNull()) {
                                tc.setId(tcNode.get("id").asText());
                            }
                            JsonNode func = tcNode.path("function");
                            if (func.has("name") && !func.get("name").isNull()) {
                                tc.setName(func.get("name").asText());
                            }
                            if (func.has("arguments") && !func.get("arguments").isNull()) {
                                tc.setArguments(tc.getArguments() + func.get("arguments").asText());
                            }
                        }
                    }

                    // finish_reason=tool_calls: 发送完成块，附带累积的工具调用
                    if ("tool_calls".equals(finishReason)) {
                        List<ChatStreamChunk.ToolCallChunk> toolCalls = acc.values().stream()
                                .sorted(java.util.Comparator.comparingInt(ChatStreamChunk.ToolCallChunk::getIndex))
                                .collect(java.util.stream.Collectors.toList());
                        return Mono.just(ChatStreamChunk.builder()
                                .done(true)
                                .finishReason("tool_calls")
                                .toolCalls(toolCalls)
                                .content("")
                                .build());
                    }

                    // --- 处理文本内容 ---
                    JsonNode contentNode = delta.get("content");
                    if (contentNode != null && !contentNode.isNull() && !contentNode.asText().isEmpty()) {
                        return Mono.just(ChatStreamChunk.content(contentNode.asText()));
                    }

                    // 普通完成
                    if (finishReason != null && !"tool_calls".equals(finishReason)) {
                        if (root.has("usage")) {
                            JsonNode usage = root.get("usage");
                            ChatResponse.Usage responseUsage = ChatResponse.Usage.of(
                                    usage.path("prompt_tokens").asInt(0),
                                    usage.path("completion_tokens").asInt(0)
                            );
                            return Mono.just(ChatStreamChunk.done(responseUsage, finishReason));
                        }
                        return Mono.just(ChatStreamChunk.done(null, finishReason));
                    }
                }

            } catch (Exception e) {
                log.warn("[OpenAI] 解析 SSE 行失败: {}, line: {}", e.getMessage(), line);
            }
        }

        return Mono.empty();
    }

    /**
     * 构建请求体
     */
    private Map<String, Object> buildOpenAiRequestBody(ChatRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.getModel());
        body.put("messages", convertMessages(request.getMessages()));
        body.put("temperature", request.getTemperature());
        body.put("max_tokens", request.getMaxTokens());
        body.put("top_p", request.getTopP());
        body.put("frequency_penalty", request.getFrequencyPenalty());
        body.put("presence_penalty", request.getPresencePenalty());

        if (request.getStop() != null) {
            body.put("stop", request.getStop());
        }
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            body.put("tools", convertTools(request.getTools()));
        }

        return body;
    }

    /**
     * 转换工具定义为 OpenAI 格式
     */
    private List<Map<String, Object>> convertTools(List<ChatRequest.ToolDefinition> tools) {
        return tools.stream()
                .map(t -> {
                    Map<String, Object> tool = new HashMap<>();
                    tool.put("type", t.getType() != null ? t.getType() : "function");
                    Map<String, Object> func = new HashMap<>();
                    func.put("name", t.getFunction().getName());
                    func.put("description", t.getFunction().getDescription());
                    if (t.getFunction().getParameters() != null) {
                        func.put("parameters", t.getFunction().getParameters());
                    }
                    tool.put("function", func);
                    return tool;
                })
                .toList();
    }

    /**
     * 转换消息格式（支持 tool_calls 和 tool 角色）
     */
    private List<Map<String, Object>> convertMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(msg -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("role", msg.getRole());

                    if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                        // assistant 消息带 tool_calls
                        m.put("content", msg.getContent());
                        m.put("tool_calls", msg.getToolCalls().stream()
                                .map(tc -> {
                                    Map<String, Object> tcMap = new HashMap<>();
                                    tcMap.put("id", tc.getId());
                                    tcMap.put("type", "function");
                                    tcMap.put("function", Map.of(
                                            "name", tc.getName(),
                                            "arguments", toJsonString(tc.getArguments())
                                    ));
                                    return tcMap;
                                })
                                .toList());
                    } else if ("tool".equals(msg.getRole())) {
                        m.put("tool_call_id", msg.getToolCallId());
                        m.put("content", msg.getContent());
                    } else {
                        m.put("content", msg.getContent());
                    }

                    if (msg.getName() != null && !"tool".equals(msg.getRole())) {
                        m.put("name", msg.getName());
                    }
                    return m;
                })
                .toList();
    }

    private String toJsonString(Map<String, Object> arguments) {
        try {
            return objectMapper.writeValueAsString(arguments);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 解析同步响应
     */
    private ChatResponse parseChatResponse(String responseJson) {
        try {
            log.info("[OpenAI] 原始响应 JSON: {}", responseJson.substring(0, Math.min(500, responseJson.length())));
            JsonNode root = objectMapper.readTree(responseJson);

            JsonNode message = root.path("choices").get(0).path("message");
            String content = message.path("content").asText();

            // 兼容"思考"类模型（MiMo/DeepSeek-R1）：content 为空时回退到 reasoning_content
            if (content.isEmpty()) {
                content = message.path("reasoning_content").asText();
                if (!content.isEmpty()) {
                    content = extractFinalAnswer(content);
                    log.info("[OpenAI] content 为空，使用 reasoning_content 提取: {}",
                            content.substring(0, Math.min(100, content.length())));
                }
            }

            log.info("[OpenAI] 解析结果: contentLength={}, content={}",
                    content.length(), content.substring(0, Math.min(100, content.length())));

            String model = root.path("model").asText();

            JsonNode usage = root.path("usage");
            ChatResponse.Usage responseUsage = ChatResponse.Usage.of(
                    usage.path("prompt_tokens").asInt(0),
                    usage.path("completion_tokens").asInt(0)
            );

            return ChatResponse.success(content, model, responseUsage);

        } catch (Exception e) {
            log.error("[OpenAI] 解析响应失败: {}", e.getMessage(), e);
            return ChatResponse.empty();
        }
    }

    /**
     * 构建请求 URL
     */
    private String buildChatUrl(Provider provider) {
        return normalizeV1BaseUrl(provider.getBaseUrl()) + "/chat/completions";
    }

    /**
     * 从 reasoning_content 提取最终答案。
     * 思考类模型（MiMo/DeepSeek-R1）在 content 为空时会输出推理链，
     * 取末尾文本作为答案（推理链末尾通常包含最终判断）。
     */
    private String extractFinalAnswer(String reasoning) {
        if (reasoning == null || reasoning.isEmpty()) {
            return "";
        }
        // 取最后 500 个字符，通常包含最终答案
        String tail = reasoning.length() > 500
                ? reasoning.substring(reasoning.length() - 500)
                : reasoning;
        // 去掉可能的工整前缀（如 "因此，我的响应应该只是..."）
        String[] markers = {"只是", "应为", "输出", "答案"};
        for (String marker : markers) {
            int idx = tail.lastIndexOf(marker);
            if (idx >= 0) {
                String answer = tail.substring(idx + marker.length()).trim();
                // 去掉尾部标点
                answer = answer.replaceAll("[。，！？；：\"'\\s]+$", "");
                if (!answer.isEmpty()) {
                    return answer;
                }
            }
        }
        // 回退：取最后一行
        String[] lines = tail.split("\\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (!line.isEmpty()) {
                return line;
            }
        }
        return tail.trim();
    }

    /**
     * 构建请求头
     */
    private Map<String, String> buildHeaders(Provider provider) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + getApiKey(provider));
        headers.put("Content-Type", "application/json");
        return headers;
    }
}
