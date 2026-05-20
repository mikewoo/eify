package com.eify.agent.domain.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 更新 Agent 请求
 */
@Data
public class AgentUpdateRequest {

    /**
     * Agent 名称
     */
    @Size(min = 2, max = 100, message = "长度在 2 到 100 个字符")
    private String name;

    /**
     * Agent 描述
     */
    @Size(max = 500, message = "描述不能超过 500 个字符")
    private String description;

    /**
     * Agent 头像
     */
    @Size(max = 500, message = "头像 URL 不能超过 500 个字符")
    private String avatar;

    /**
     * 默认供应商 ID
     */
    private Long defaultProviderId;

    /**
     * 默认模型
     */
    @Size(max = 100, message = "模型名称不能超过 100 个字符")
    private String defaultModel;

    /**
     * 系统提示词
     */
    @Size(max = 10000, message = "系统提示词不能超过 10000 个字符")
    private String systemPrompt;

    /**
     * 用户消息前缀
     */
    @Size(max = 1000, message = "用户消息前缀不能超过 1000 个字符")
    private String userMessagePrefix;

    /**
     * 欢迎语
     */
    @Size(max = 500, message = "欢迎语不能超过 500 个字符")
    private String welcomeMessage;

    /**
     * 温度：0-2
     */
    @DecimalMin(value = "0.0", message = "温度不能小于 0")
    @DecimalMax(value = "2.0", message = "温度不能大于 2")
    private BigDecimal temperature;

    /**
     * 最大 tokens
     */
    @Min(value = 1, message = "最大 tokens 至少为 1")
    @Max(value = 128000, message = "最大 tokens 不能超过 128000")
    private Integer maxTokens;

    /**
     * Top-p 采样
     */
    @DecimalMin(value = "0.0", message = "Top-p 不能小于 0")
    @DecimalMax(value = "1.0", message = "Top-p 不能大于 1")
    private BigDecimal topP;

    /**
     * 频率惩罚
     */
    @DecimalMin(value = "-2.0", message = "频率惩罚不能小于 -2")
    @DecimalMax(value = "2.0", message = "频率惩罚不能大于 2")
    private BigDecimal frequencyPenalty;

    /**
     * 存在惩罚
     */
    @DecimalMin(value = "-2.0", message = "存在惩罚不能小于 -2")
    @DecimalMax(value = "2.0", message = "存在惩罚不能大于 2")
    private BigDecimal presencePenalty;

    /**
     * 最大历史轮数
     */
    @Min(value = 0, message = "历史轮数不能为负数")
    @Max(value = 100, message = "历史轮数不能超过 100")
    private Integer maxHistoryRounds;

    /**
     * 是否启用流式输出
     */
    private Integer streamEnabled;

    /**
     * 扩展配置
     */
    private Object agentConfig;

    /**
     * 启用状态
     */
    private Integer enabled;

    /**
     * 关联的知识库 ID 列表
     */
    private List<java.lang.Long> knowledgeIds;

    /**
     * 关联的 MCP 工具 ID 列表
     */
    private List<java.lang.Long> mcpToolIds;

    /**
     * 是否启用 RAG
     */
    private Integer ragEnabled;

    /**
     * RAG 检索 Top-K
     */
    private Integer ragTopK;

    /**
     * 检索策略
     */
    private String ragStrategy;
}
