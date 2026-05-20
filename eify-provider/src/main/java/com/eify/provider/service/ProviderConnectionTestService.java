package com.eify.provider.service;

import com.eify.provider.domain.dto.ConnectionTestResult;
import com.eify.provider.domain.entity.Provider;

/**
 * 供应商连通性测试服务
 */
public interface ProviderConnectionTestService {

    /**
     * 测试供应商连通性
     * <p>
     * 根据 provider.type 分发到不同的 API 调用：
     * <ul>
     *   <li>OPENAI: GET /v1/models (Bearer Token)</li>
     *   <li>OPENAI_COMPATIBLE: GET /v1/models (Bearer Token)</li>
     *   <li>ANTHROPIC: GET /v1/models (x-api-key + anthropic-version)</li>
     *   <li>OLLAMA: GET /api/tags (无认证)</li>
     * </ul>
     *
     * @param provider 供应商配置
     * @return 测试结果
     */
    ConnectionTestResult testConnection(Provider provider);

    /**
     * 根据 provider ID 测试连通性
     *
     * @param providerId 供应商 ID
     * @return 测试结果
     */
    ConnectionTestResult testConnectionById(Long providerId);
}
