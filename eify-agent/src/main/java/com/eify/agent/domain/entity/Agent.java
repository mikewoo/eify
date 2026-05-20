package com.eify.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import tools.jackson.databind.JsonNode;
import com.eify.common.handler.JsonTypeHandler;
import com.eify.common.entity.BaseEntity;
import com.eify.common.workspace.WorkspaceAware;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_agent")
public class Agent extends BaseEntity implements WorkspaceAware {

    /**
     * Agent 名称
     */
    private String name;

    @TableField("workspace_id")
    private Long workspaceId;

    /**
     * Agent 描述
     */
    private String description;

    /**
     * Agent 头像 URL
     */
    private String avatar;

    /**
     * 默认供应商 ID
     */
    @TableField("default_provider_id")
    private Long defaultProviderId;

    /**
     * 默认模型：gpt-4, claude-3-opus 等
     */
    @TableField("default_model")
    private String defaultModel;

    /**
     * 系统提示词
     */
    private String systemPrompt;

    /**
     * 用户消息前缀
     */
    @TableField("user_message_prefix")
    private String userMessagePrefix;

    /**
     * 欢迎语
     */
    private String welcomeMessage;

    /**
     * 温度：0-2，越高越随机
     */
    private BigDecimal temperature;

    /**
     * 最大生成 tokens
     */
    @TableField("max_tokens")
    private Integer maxTokens;

    /**
     * Top-p 采样
     */
    @TableField("top_p")
    private BigDecimal topP;

    /**
     * 频率惩罚：-2 到 2
     */
    @TableField("frequency_penalty")
    private BigDecimal frequencyPenalty;

    /**
     * 存在惩罚：-2 到 2
     */
    @TableField("presence_penalty")
    private BigDecimal presencePenalty;

    /**
     * 最大历史轮数
     */
    @TableField("max_history_rounds")
    private Integer maxHistoryRounds;

    /**
     * 是否启用流式输出
     */
    @TableField("stream_enabled")
    private Integer streamEnabled;

    /**
     * 扩展配置（JSON）
     * <pre>
     * {
     *   "tools": [{"type": "function", "function": {...}}],
     *   "response_format": {"type": "json_object"},
     *   "seed": 42,
     *   "stop": ["\n"],
     *   "logit_bias": {}
     * }
     * </pre>
     */
    @TableField(value = "agent_config", typeHandler = JsonTypeHandler.class)
    private JsonNode agentConfig;

    /**
     * 启用状态：0-禁用，1-启用
     */
    private Integer enabled;

    // ==================== RAG 配置 ====================

    /**
     * 关联的知识库 ID 列表（不持久化到 ai_agent 表，通过 agent_knowledge 关联表维护）
     */
    @TableField(exist = false)
    private List<Long> knowledgeIds;

    /**
     * 关联的 MCP 工具 ID 列表（不持久化到 ai_agent 表，通过 agent_mcp_tool 关联表维护）
     */
    @TableField(exist = false)
    private List<Long> mcpToolIds;

    /**
     * 是否启用 RAG：0=禁用，1=启用
     */
    @TableField("rag_enabled")
    private Integer ragEnabled = 0;

    /**
     * RAG 检索返回的片段数量
     */
    @TableField("rag_top_k")
    private Integer ragTopK = 5;

    /**
     * 检索策略：vector / keyword / hybrid
     */
    @TableField("rag_strategy")
    private String ragStrategy = "hybrid";

    /**
     * 绑定工作流 ID，不为空时用户消息走工作流编排而非直接 LLM
     */
    @TableField("workflow_id")
    private Long workflowId;

    /**
     * 创建人 ID
     */
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private Long creatorId;
}
