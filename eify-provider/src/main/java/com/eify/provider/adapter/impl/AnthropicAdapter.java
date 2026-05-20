package com.eify.provider.adapter.impl;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
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
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Anthropic Claude 适配器
 */
@Component
public class AnthropicAdapter extends AbstractProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(AnthropicAdapter.class);
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final WebClient webClient;
    private final ObjectMapper localObjectMapper = new ObjectMapper();

    public AnthropicAdapter(LlmHttpClient llmHttpClient, WebClient.Builder webClientBuilder) {
        super(llmHttpClient);
        this.webClient = webClientBuilder.build();
    }

    @Override
    public ProviderType getSupportedType() {
        return ProviderType.ANTHROPIC;
    }

    @Override
    protected String doTest(Provider provider) throws Exception {
        String url = buildModelsUrl(provider.getBaseUrl());

        Map<String, String> headers = new HashMap<>();
        headers.put("x-api-key", getApiKey(provider));
        headers.put("anthropic-version", ANTHROPIC_VERSION);

        return llmHttpClient.get(url, headers, TEST_TIMEOUT_SECONDS);
    }

    @Override
    protected ChatResponse doChat(Provider provider, ChatRequest request) {
        try {
            String url = buildMessagesUrl(provider);
            String jsonBody = buildRequestBody(request);
            Map<String, String> headers = buildHeaders(provider);

            String response = llmHttpClient.post(url, headers, jsonBody);
            return parseChatResponse(response);

        } catch (Exception e) {
            log.error("[Anthropic] 同步对话失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.PROVIDER_CALL_FAILED);
        }
    }

    @Override
    protected Flux<ChatStreamChunk> doStreamChat(Provider provider, ChatRequest request) {
        String url = buildMessagesUrl(provider);
        String jsonBody = buildStreamRequestBody(request);
        Map<String, String> headers = buildHeaders(provider);

        long startTime = System.currentTimeMillis();
        log.info("[Anthropic] 发起流式对话 - URL: {}, Model: {}", url, request.getModel());

        AtomicBoolean firstChunk = new AtomicBoolean(true);

        JsonNode bodyNode;
        try {
            bodyNode = localObjectMapper.readTree(jsonBody);
        } catch (Exception e) {
            return Flux.error(new BusinessException(ErrorCode.PARAM_ERROR, "请求体解析失败"));
        }

        return webClient.post()
                .uri(url)
                .headers(h -> headers.forEach(h::set))
                .bodyValue(bodyNode)
                .retrieve()
                .bodyToFlux(org.springframework.core.io.buffer.DataBuffer.class)
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                })
                .flatMap(text -> {
                    String[] events = text.split("\\r?\\n\\r?\\n");
                    return Flux.fromArray(events);
                })
                .flatMap(this::parseSseEvent)
                .doOnNext(chunk -> {
                    if (firstChunk.compareAndSet(true, false)) {
                        long ttfb = System.currentTimeMillis() - startTime;
                        log.info("[Anthropic] 首字节时间(TTFB) - Model: {}, {}ms", request.getModel(), ttfb);
                    }
                })
                .doOnCancel(() -> log.warn("[Anthropic] 客户端断开连接 - Model: {}", request.getModel()))
                .doOnError(error -> log.error("[Anthropic] SSE 流式调用出错 - Model: {}, Error: {}",
                        request.getModel(), error.getMessage()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<ChatStreamChunk> parseSseEvent(String line) {
        if (line == null || line.isEmpty()) return Mono.empty();

        // Anthropic SSE: event: xxx\ndata: {...}
        String[] parts = line.split("\\r?\\n");
        String eventType = null;
        String dataJson = null;

        for (String part : parts) {
            if (part.startsWith("event: ")) {
                eventType = part.substring(7).trim();
            } else if (part.startsWith("data: ")) {
                dataJson = part.substring(6).trim();
            }
        }

        if (dataJson == null) return Mono.empty();

        try {
            JsonNode root = localObjectMapper.readTree(dataJson);

            switch (eventType != null ? eventType : "") {
                case "message_start":
                    return Mono.empty();

                case "content_block_start":
                    return Mono.empty();

                case "content_block_delta":
                    JsonNode delta = root.path("delta");
                    if ("text_delta".equals(delta.path("type").asText())) {
                        String text = delta.path("text").asText();
                        if (!text.isEmpty()) {
                            return Mono.just(ChatStreamChunk.content(text));
                        }
                    }
                    return Mono.empty();

                case "content_block_stop":
                    return Mono.empty();

                case "message_delta":
                    JsonNode usage = root.path("usage");
                    int outputTokens = usage.path("output_tokens").asInt(0);
                    String stopReason = root.path("delta").path("stop_reason").asText();
                    return Mono.just(ChatStreamChunk.done(
                            ChatResponse.Usage.of(0, outputTokens),
                            stopReason != null && !stopReason.isEmpty() ? stopReason : "end_turn"));

                case "message_stop":
                    return Mono.empty();

                case "ping":
                    return Mono.empty();

                default:
                    // Non-streaming events or unrecognized
                    return Mono.empty();
            }

        } catch (Exception e) {
            log.warn("[Anthropic] 解析 SSE 事件失败: {}, line: {}", e.getMessage(),
                    line.substring(0, Math.min(200, line.length())));
            return Mono.empty();
        }
    }

    private String buildRequestBody(ChatRequest request) {
        try {
            ObjectNode root = localObjectMapper.createObjectNode();
            root.put("model", request.getModel());
            root.put("max_tokens", request.getMaxTokens() != null ? request.getMaxTokens() : 4096);
            root.set("messages", convertMessages(request.getMessages()));

            if (request.getTemperature() != null) root.put("temperature", request.getTemperature());
            if (request.getTopP() != null) root.put("top_p", request.getTopP());

            return localObjectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "构建请求体失败: " + e.getMessage());
        }
    }

    private String buildStreamRequestBody(ChatRequest request) {
        try {
            ObjectNode root = (ObjectNode) localObjectMapper.readTree(buildRequestBody(request));
            root.put("stream", true);
            return localObjectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "构建流式请求体失败: " + e.getMessage());
        }
    }

    private ArrayNode convertMessages(List<ChatMessage> messages) {
        ArrayNode array = localObjectMapper.createArrayNode();
        for (ChatMessage msg : messages) {
            ObjectNode node = array.addObject();
            node.put("role", msg.getRole());
            // Anthropic uses content array with text blocks for assistant, string for user
            if ("user".equals(msg.getRole())) {
                node.put("content", msg.getContent());
            } else {
                ArrayNode contentArray = node.putArray("content");
                ObjectNode textBlock = contentArray.addObject();
                textBlock.put("type", "text");
                textBlock.put("text", msg.getContent() != null ? msg.getContent() : "");
            }
        }
        return array;
    }

    private ChatResponse parseChatResponse(String responseJson) {
        try {
            JsonNode root = localObjectMapper.readTree(responseJson);
            JsonNode content = root.path("content");
            StringBuilder text = new StringBuilder();
            if (content.isArray()) {
                for (JsonNode block : content) {
                    if ("text".equals(block.path("type").asText())) {
                        text.append(block.path("text").asText());
                    }
                }
            }
            JsonNode usage = root.path("usage");
            ChatResponse.Usage responseUsage = ChatResponse.Usage.of(
                    usage.path("input_tokens").asInt(0),
                    usage.path("output_tokens").asInt(0));

            return ChatResponse.success(text.toString(), root.path("model").asText(), responseUsage);
        } catch (Exception e) {
            log.error("[Anthropic] 解析响应失败: {}", e.getMessage(), e);
            return ChatResponse.empty();
        }
    }

    private String buildMessagesUrl(Provider provider) {
        String baseUrl = normalizeBaseUrl(provider.getBaseUrl());
        return baseUrl + "/v1/messages";
    }

    private Map<String, String> buildHeaders(Provider provider) {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-api-key", getApiKey(provider));
        headers.put("anthropic-version", ANTHROPIC_VERSION);
        headers.put("Content-Type", "application/json");
        return headers;
    }
}
