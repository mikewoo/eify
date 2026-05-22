package com.eify.knowledge.route;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import tools.jackson.databind.JsonNode;
import com.eify.knowledge.config.EmbeddingConfig;
import com.eify.knowledge.domain.entity.KnowledgeBase;
import com.eify.provider.adapter.ProviderAdapter;
import com.eify.provider.adapter.ProviderAdapterFactory;
import com.eify.provider.domain.entity.ModelConfig;
import com.eify.provider.domain.entity.Provider;
import com.eify.provider.mapper.ModelConfigMapper;
import com.eify.provider.service.ProviderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 嵌入模型路由解析器 —— 连接 KnowledgeBase 与 Provider/ModelConfig 体系的桥梁。
 * <p>
 * 从 KnowledgeBase.embeddingModelId 反查 Provider 凭证 + ModelConfig 配置，
 * 构建 EmbeddingRoute 供 EmbeddingStrategyImpl 使用。
 * 若 embeddingModelId 为空或关联的 Provider/ModelConfig 不可用，返回 empty route 触发降级。
 * <p>
 * 安全约束：所有查询均过滤 workspace_id，确保不会跨工作空间泄露 API 凭据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingRouteResolver {

    private final ProviderService providerService;
    private final ModelConfigMapper modelConfigMapper;
    private final EmbeddingConfig config;
    private final ProviderAdapterFactory adapterFactory;

    /**
     * 从 KnowledgeBase 解析嵌入路由。
     *
     * @return 有效 route 或 EmbeddingRoute.empty()
     */
    public EmbeddingRoute resolve(KnowledgeBase kb) {
        if (kb == null || kb.getEmbeddingModelId() == null) {
            return EmbeddingRoute.empty();
        }

        try {
            // 工作空间隔离：按 ID + workspace_id 双重条件查询，防止跨 workspace 凭据泄露
            ModelConfig modelConfig = modelConfigMapper.selectOne(new LambdaQueryWrapper<ModelConfig>()
                    .eq(ModelConfig::getId, kb.getEmbeddingModelId())
                    .eq(ModelConfig::getWorkspaceId, kb.getWorkspaceId())
                    .eq(ModelConfig::getEnabled, 1));
            if (modelConfig == null) {
                log.warn("[EmbeddingRoute] ModelConfig 不存在、已禁用或 workspace 不一致: id={}, ws={}, fallback to global config",
                        kb.getEmbeddingModelId(), kb.getWorkspaceId());
                return EmbeddingRoute.empty();
            }

            Provider provider = providerService.getEntityById(modelConfig.getProviderId());
            if (provider == null || provider.getEnabled() == null || provider.getEnabled() != 1) {
                log.warn("[EmbeddingRoute] Provider 不存在或已禁用: id={}, fallback to global config",
                        modelConfig.getProviderId());
                return EmbeddingRoute.empty();
            }

            // 二次校验：ModelConfig 与 Provider 必须属于同一 workspace
            if (!kb.getWorkspaceId().equals(provider.getWorkspaceId())) {
                log.error("[EmbeddingRoute] Provider workspace 不一致: providerWs={}, kbWs={}, fallback to global config",
                        provider.getWorkspaceId(), kb.getWorkspaceId());
                return EmbeddingRoute.empty();
            }

            String apiKey = extractApiKey(provider.getAuthConfig());
            String apiUrl = buildEmbeddingUrl(provider);
            int dimension = kb.getVectorDimension() != null && kb.getVectorDimension() > 0
                    ? kb.getVectorDimension()
                    : config.getDimension();

            return EmbeddingRoute.builder()
                    .apiUrl(apiUrl)
                    .apiKey(apiKey)
                    .modelId(modelConfig.getModelId())
                    .dimension(dimension)
                    .modelConfigId(modelConfig.getId())
                    .build();
        } catch (Exception e) {
            log.error("[EmbeddingRoute] 解析失败, fallback to global config - kbId={}", kb.getId(), e);
            return EmbeddingRoute.empty();
        }
    }

    /**
     * 拼接嵌入 API 端点，端点路径由 ProviderAdapter 决定。
     * 按端点特征选择 URL 规范化策略：OpenAI 兼容类统一到 /v1 层级，Ollama 类直接拼接。
     */
    private String buildEmbeddingUrl(Provider provider) {
        String baseUrl = provider.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }

        ProviderAdapter adapter = adapterFactory.getAdapter(provider.getType());
        String endpoint = adapter.getEmbeddingEndpoint();

        String normalized = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;

        // Ollama 类不使用 /v1 规范，去除 baseUrl 中可能残留的 /v1 后缀后直接拼接
        if (endpoint.startsWith("/api/")) {
            if (normalized.endsWith("/v1")) {
                normalized = normalized.substring(0, normalized.length() - 3);
            }
            return normalized + endpoint;
        }

        // OpenAI 兼容规范：统一到 /v1 层级
        int v1SlashIndex = normalized.indexOf("/v1/");
        if (v1SlashIndex >= 0) {
            normalized = normalized.substring(0, v1SlashIndex + 3);
        }
        if (!normalized.endsWith("/v1")) {
            normalized += "/v1";
        }
        return normalized + endpoint;
    }

    /**
     * 从 Provider.authConfig JSON 中提取 API Key。
     */
    private String extractApiKey(JsonNode authConfig) {
        if (authConfig == null) {
            return null;
        }
        if (authConfig.has("api_key")) {
            return authConfig.get("api_key").asText();
        }
        if (authConfig.has("apiKey")) {
            return authConfig.get("apiKey").asText();
        }
        return null;
    }
}
