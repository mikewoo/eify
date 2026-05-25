package com.eify.agent.domain.dto;

import tools.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Agent 响应对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResponse {

    /**
     * Agent ID
     */
    private Long id;

    /**
     * Agent 名称
     */
    private String name;

    /**
     * Agent 描述
     */
    private String description;

    /**
     * Agent 头像
     */
    private String avatar;

    /**
     * 默认供应商 ID
     */
    private Long defaultProviderId;

    /**
     * 默认供应商名称（关联查询）
     */
    private String defaultProviderName;

    /**
     * 默认供应商类型（关联查询）
     */
    private String defaultProviderType;

    /**
     * 默认供应商是否可用（false 表示已被删除或禁用）
     */
    private Boolean defaultProviderAvailable;

    /**
     * 默认模型
     */
    private String defaultModel;

    /**
     * 系统提示词
     */
    private String systemPrompt;

    /**
     * 用户消息前缀
     */
    private String userMessagePrefix;

    /**
     * 欢迎语
     */
    private String welcomeMessage;

    /**
     * 温度
     */
    private BigDecimal temperature;

    /**
     * 最大 tokens
     */
    private Integer maxTokens;

    /**
     * Top-p
     */
    private BigDecimal topP;

    /**
     * 频率惩罚
     */
    private BigDecimal frequencyPenalty;

    /**
     * 存在惩罚
     */
    private BigDecimal presencePenalty;

    /**
     * 最大历史轮数
     */
    private Integer maxHistoryRounds;

    /**
     * 流式输出启用
     */
    private Integer streamEnabled;

    /**
     * 扩展配置
     */
    private JsonNode agentConfig;

    /**
     * 启用状态
     */
    private Integer enabled;

    /**
     * 创建时间
     */
    private String createdAt;

    /**
     * 更新时间
     */
    private String updatedAt;

    /**
     * 创建人 ID
     */
    private Long creatorId;

    // ==================== RAG 配置 ====================

    private List<java.lang.Long> knowledgeIds;
    private Integer ragEnabled;
    private Integer ragTopK;
    private String ragStrategy;

    /**
     * 关联的知识库简要信息（用于前端展示）
     */
    private List<KnowledgeBaseBrief> knowledgeBases;

    // ==================== MCP 工具 ====================

    private List<java.lang.Long> mcpToolIds;

    /**
     * 关联的 MCP 工具简要信息（用于前端展示）
     */
    private List<McpToolBrief> mcpTools;

    // ==================== 嵌套对象（可选）====================

    /**
     * 默认供应商信息（关联查询）
     */
    private ProviderInfo defaultProvider;

    /**
     * 供应商信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProviderInfo {
        private Long id;
        private String name;
        private String type;
        private String baseUrl;
    }

    /**
     * 知识库简要信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KnowledgeBaseBrief {
        private Long id;
        private String name;
    }

    /**
     * MCP 工具简要信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class McpToolBrief {
        private Long id;
        private String name;
        private String serverName;
    }
}
