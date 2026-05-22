package com.eify.knowledge.strategy;

import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import com.eify.common.http.LlmHttpClient;
import com.eify.knowledge.config.EmbeddingConfig;
import com.eify.knowledge.route.EmbeddingRoute;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;


/**
 * 嵌入策略实现
 *
 * 使用 Provider 模块的 LlmHttpClient 进行嵌入模型调用
 * 支持异步批量处理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingStrategyImpl implements EmbeddingStrategy {

    private final LlmHttpClient httpClient;
    private final EmbeddingConfig config;
    private final ObjectMapper objectMapper;
    private final Executor embeddingExecutor;

    private Map<String, String> createHeaders(EmbeddingRoute route) {
        Map<String, String> headers = new HashMap<>();
        String apiKey = route.isPresent() && route.getApiKey() != null
                ? route.getApiKey()
                : config.getApiKey();
        if (apiKey != null && !apiKey.isEmpty()) {
            headers.put("Authorization", "Bearer " + apiKey);
        }
        return headers;
    }

    private String getEffectiveApiUrl(EmbeddingRoute route) {
        return route.isPresent() ? route.getApiUrl() : config.getApiUrl();
    }

    private String getEffectiveModel(EmbeddingRoute route) {
        return route.isPresent() ? route.getModelId() : config.getModel();
    }

    @Override
    public float[] embed(String text) {
        return embed(text, EmbeddingRoute.empty());
    }

    @Override
    public float[] embed(String text, EmbeddingRoute route) {
        try {
            String apiUrl = getEffectiveApiUrl(route);
            String model = getEffectiveModel(route);
            JsonNode request = objectMapper.createObjectNode()
                .put("input", text)
                .put("model", model);

            String body = objectMapper.writeValueAsString(request);
            String responseStr = httpClient.post(apiUrl, createHeaders(route), body);
            JsonNode response = objectMapper.readTree(responseStr);

            JsonNode embeddings = response.get("data");
            if (embeddings != null && embeddings.isArray() && embeddings.size() > 0) {
                JsonNode embedding = embeddings.get(0).get("embedding");
                return toArray(embedding);
            }

            throw new BusinessException(ErrorCode.EMBEDDING_FAILED);
        } catch (Exception e) {
            log.error("Embedding failed for text: {}", text.length() > 50 ? text.substring(0, 50) : text, e);
            throw new BusinessException(ErrorCode.EMBEDDING_FAILED);
        }
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        return embedBatch(texts, EmbeddingRoute.empty());
    }

    @Override
    public List<float[]> embedBatch(List<String> texts, EmbeddingRoute route) {
        int batchSize = Math.min(config.getMaxBatchSize(), 10);
        if (texts.size() <= batchSize) {
            return embedBatchInternal(texts, route);
        }

        List<List<String>> batches = partition(texts, batchSize);
        log.info("[Embedding] 分批处理: {} 个文本, {} 批, 每批 {}", texts.size(), batches.size(), batchSize);

        List<float[]> results = new ArrayList<>();
        for (List<String> batch : batches) {
            results.addAll(embedBatchInternal(batch, route));
        }
        return results;
    }

    private List<float[]> embedBatchInternal(List<String> texts, EmbeddingRoute route) {
        try {
            String apiUrl = getEffectiveApiUrl(route);
            String model = getEffectiveModel(route);
            CompletableFuture<List<float[]>> future = CompletableFuture.supplyAsync(() -> {
                try {
                    JsonNode request = objectMapper.createObjectNode()
                        .put("model", model)
                        .set("input", objectMapper.valueToTree(texts));

                    String body = objectMapper.writeValueAsString(request);
                    String responseStr = httpClient.post(apiUrl, createHeaders(route), body);
                    JsonNode response = objectMapper.readTree(responseStr);

                    List<float[]> embeddings = new ArrayList<>();
                    JsonNode data = response.get("data");
                    if (data != null && data.isArray()) {
                        for (JsonNode item : data) {
                            JsonNode embedding = item.get("embedding");
                            embeddings.add(toArray(embedding));
                        }
                    }
                    return embeddings;
                } catch (Exception e) {
                    log.error("Batch embedding failed", e);
                    throw new BusinessException(ErrorCode.EMBEDDING_FAILED);
                }
            }, embeddingExecutor);

            return future.get(config.getTimeout(), java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("Batch embedding timeout", e);
            throw new BusinessException(ErrorCode.EMBEDDING_FAILED);
        }
    }

    @Override
    public int getDimension() {
        return config.getDimension();
    }

    @Override
    public String getModelName() {
        return config.getModel();
    }

    @Override
    public boolean isHealthy() {
        try {
            // 简单的健康检查
            float[] test = embed("test");
            return test != null && test.length > 0;
        } catch (Exception e) {
            log.warn("Embedding health check failed", e);
            return false;
        }
    }

    private float[] toArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return new float[0];
        }
        float[] result = new float[node.size()];
        for (int i = 0; i < node.size(); i++) {
            result[i] = node.get(i).floatValue();
        }
        return result;
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}