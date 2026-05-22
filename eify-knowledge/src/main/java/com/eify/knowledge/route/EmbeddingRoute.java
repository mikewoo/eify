package com.eify.knowledge.route;

import lombok.Builder;
import lombok.Value;

/**
 * 嵌入模型路由配置 —— 从 Provider/ModelConfig 系统解析出的调用凭据。
 * <p>
 * 当 KnowledgeBase 绑定了 embeddingModelId 时，EmbeddingRouteResolver 构建此对象；
 * EmbeddingStrategyImpl 优先使用 route 中的值，route 为空时降级到 application.yml 全局配置。
 */
@Value
@Builder
public class EmbeddingRoute {

    /** 嵌入 API 端点，如 https://api.openai.com/v1/embeddings */
    String apiUrl;

    /** Bearer token */
    String apiKey;

    /** 传给 API 的 model 参数值 */
    String modelId;

    /** 输出向量维度 */
    int dimension;

    /** 来源 ModelConfig ID（追踪用） */
    Long modelConfigId;

    /**
     * 空路由 —— 表示无 KB 级别覆盖，调用方应降级到全局 EmbeddingConfig。
     */
    public static EmbeddingRoute empty() {
        return EmbeddingRoute.builder().dimension(0).build();
    }

    /**
     * 是否包含有效的路由配置。
     */
    public boolean isPresent() {
        return apiUrl != null && !apiUrl.isBlank()
                && modelId != null && !modelId.isBlank();
    }
}
