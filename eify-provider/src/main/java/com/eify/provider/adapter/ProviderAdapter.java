package com.eify.provider.adapter;

import com.eify.provider.domain.entity.Provider;
import com.eify.provider.domain.dto.ChatRequest;
import com.eify.provider.domain.dto.ChatResponse;
import com.eify.provider.domain.dto.ChatStreamChunk;
import com.eify.provider.domain.dto.ConnectionTestResult;
import reactor.core.publisher.Flux;

/**
 * 供应商适配器接口
 * <p>
 * 每个供应商实现自己的连通性测试和对话逻辑
 */
public interface ProviderAdapter {

    /**
     * 测试连通性
     *
     * @param provider 供应商配置
     * @return 测试结果
     */
    ConnectionTestResult testConnection(Provider provider);

    /**
     * 获取支持的供应商类型
     */
    com.eify.provider.constant.ProviderType getSupportedType();

    /**
     * 同步对话
     * <p>
     * 发送请求并等待完整响应返回
     *
     * @param provider 供应商配置
     * @param request  对话请求
     * @return 对话响应
     */
    ChatResponse chat(Provider provider, ChatRequest request);

    /**
     * 流式对话
     * <p>
     * 返回响应式流，实时推送生成的内容
     *
     * @param provider 供应商配置
     * @param request  对话请求
     * @return 响应式流，每个元素是一个内容块
     */
    Flux<ChatStreamChunk> streamChat(Provider provider, ChatRequest request);

    /**
     * 获取 Embedding API 端点路径（相对路径）。
     * 默认返回 OpenAI 兼容的 /embeddings，子类可覆盖。
     */
    default String getEmbeddingEndpoint() {
        return "/embeddings";
    }
}
