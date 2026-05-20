package com.eify.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 嵌入模型配置
 *
 * 支持多种嵌入模型配置：
 * - OpenAI text-embedding-ada-002
 * - OpenAI text-embedding-3-small
 * - Ollama 嵌入模型
 * - 其他兼容模型
 *
 * 配置示例：
 * knowledge:
 *   embedding:
 *     provider: openai  # 或 ollama
 *     model: text-embedding-ada-002
 *     dimension: 1536
 *     timeout: 30000
 *     max-batch-size: 100
 */
@Data
@Component
@ConfigurationProperties(prefix = "knowledge.embedding")
public class EmbeddingConfig {

    /**
     * 嵌入模型提供商
     * openai, ollama, custom
     */
    private String provider = "openai";

    /**
     * 模型名称
     */
    private String model = "text-embedding-ada-002";

    /**
     * 向量维度
     * OpenAI: 1536
     * Ollama: 默认 768
     */
    private int dimension = 1536;

    /**
     * API 端点
     * 默认从 provider 配置继承
     */
    private String apiUrl;

    /**
     * API Key
     */
    private String apiKey;

    /**
     * 超时时间（毫秒）
     */
    private int timeout = 30000;

    /**
     * 最大批大小
     */
    private int maxBatchSize = 100;

    /**
     * 是否启用缓存
     */
    private boolean enableCache = true;

    /**
     * 缓存过期时间（秒）
     */
    private long cacheExpireTime = 3600; // 1小时

    /**
     * 重试次数
     */
    private int retryTimes = 3;

    /**
     * 重试间隔（毫秒）
     */
    private long retryInterval = 1000;

    /**
     * 是否启用异步处理
     */
    private boolean enableAsync = true;

    /**
     * 线程池大小
     */
    private int threadPoolSize = 5;
}