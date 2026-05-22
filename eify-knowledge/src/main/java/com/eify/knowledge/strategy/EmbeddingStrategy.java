package com.eify.knowledge.strategy;

import com.eify.knowledge.route.EmbeddingRoute;

import java.util.List;

/**
 * 嵌入策略接口
 *
 * 支持多种嵌入模型：
 * - OpenAI text-embedding-ada-002 (1536维)
 * - OpenAI text-embedding-3-small (1536维)
 * - Ollama 本地嵌入模型
 * - 其他兼容的嵌入模型
 *
 * 扩展点：
 * - 支持自定义嵌入模型
 * - 批量嵌入优化
 * - 缓存机制
 * - 降级策略
 */
public interface EmbeddingStrategy {

    /**
     * 文本嵌入
     *
     * @param text 输入文本
     * @return 向量数组
     */
    float[] embed(String text);

    /**
     * 批量文本嵌入
     *
     * @param texts 输入文本列表
     * @return 向量数组列表
     */
    List<float[]> embedBatch(List<String> texts);

    /**
     * 获取向量维度
     *
     * @return 向量维度
     */
    int getDimension();

    /**
     * 获取模型名称
     *
     * @return 模型名称
     */
    String getModelName();

    /**
     * 健康检查
     *
     * @return 是否健康
     */
    boolean isHealthy();

    /**
     * 文本嵌入（按知识库路由）
     *
     * @param text 输入文本
     * @param route 嵌入模型路由，empty 时降级到全局配置
     * @return 向量数组
     */
    float[] embed(String text, EmbeddingRoute route);

    /**
     * 批量文本嵌入（按知识库路由）
     *
     * @param texts 输入文本列表
     * @param route 嵌入模型路由，empty 时降级到全局配置
     * @return 向量数组列表
     */
    List<float[]> embedBatch(List<String> texts, EmbeddingRoute route);

    /**
     * 支持的最大批大小
     *
     * @return 最大批大小
     */
    default int getMaxBatchSize() {
        return 100;
    }

    /**
     * 超时时间（毫秒）
     *
     * @return 超时时间
     */
    default int getTimeout() {
        return 30000; // 30秒
    }
}