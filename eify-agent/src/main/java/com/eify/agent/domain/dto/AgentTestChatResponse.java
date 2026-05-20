package com.eify.agent.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 测试对话响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTestChatResponse {

    /**
     * AI 回复内容
     */
    private String reply;

    /**
     * Token 统计
     */
    private TokenStats tokens;

    /**
     * 性能指标
     */
    private PerformanceMetrics performance;

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 错误信息（失败时）
     */
    private String errorMessage;

    /**
     * Token 统计
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenStats {
        /**
         * 提示词 tokens
         */
        private Integer promptTokens;

        /**
         * 完成 tokens
         */
        private Integer completionTokens;

        /**
         * 总 tokens
         */
        private Integer totalTokens;
    }

    /**
     * 性能指标
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceMetrics {
        /**
         * 延迟（毫秒）
         */
        private Long latencyMs;

        /**
         * 首字延迟（毫秒）
         */
        private Long firstTokenLatencyMs;

        /**
         * 实际使用的供应商 ID
         */
        private Long actualProviderId;

        /**
         * 实际使用的模型
         */
        private String actualModel;
    }
}
