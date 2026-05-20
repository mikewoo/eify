package com.eify.provider.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天响应
 * <p>
 * 统一的对话响应格式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /**
     * 生成的内容
     */
    private String content;

    /**
     * 使用的模型
     */
    private String model;

    /**
     * Token 使用统计
     */
    private Usage usage;

    /**
     * 完成原因
     * <p>
     * stop: 自然结束
     * length: 达到 max_tokens
     * content_filter: 内容过滤
     */
    private String finishReason;

    /**
     * 响应 ID
     */
    private String id;

    /**
     * Token 使用统计
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {

        /**
         * 输入 Token 数（提示词 + 历史消息）
         */
        private Integer promptTokens;

        /**
         * 输出 Token 数（AI 生成）
         */
        private Integer completionTokens;

        /**
         * 总 Token 数
         */
        private Integer totalTokens;

        /**
         * 计算总 Token 数
         */
        public static Usage of(int promptTokens, int completionTokens) {
            return Usage.builder()
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(promptTokens + completionTokens)
                    .build();
        }
    }

    /**
     * 创建成功响应
     */
    public static ChatResponse success(String content, String model, Usage usage) {
        return ChatResponse.builder()
                .content(content)
                .model(model)
                .usage(usage)
                .finishReason("stop")
                .build();
    }

    /**
     * 创建空响应
     */
    public static ChatResponse empty() {
        return ChatResponse.builder()
                .content("")
                .finishReason("stop")
                .build();
    }
}
