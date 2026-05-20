package com.eify.agent.controller;

import com.eify.common.result.PageResult;
import com.eify.common.result.Result;
import com.eify.agent.domain.dto.*;
import com.eify.agent.domain.entity.Agent;
import com.eify.agent.service.AgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * Agent Controller
 */
@Tag(name = "Agent 管理", description = "AI Agent CRUD 接口")
@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    /**
     * 分页查询 Agent
     */
    @GetMapping
    @Operation(summary = "分页查询 Agent", description = "支持分页查询 Agent 列表，可按名称和启用状态筛选")
    public Result<PageResult<AgentResponse>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer enabled
    ) {
        PageResult<AgentResponse> result = agentService.list(page, pageSize, name, enabled);
        return Result.success(result);
    }

    /**
     * 根据 ID 查询 Agent
     */
    @GetMapping("/{id}")
    @Operation(summary = "查询 Agent 详情", description = "返回 Agent 详情，包含关联的 Provider 信息")
    public Result<AgentResponse> getById(@PathVariable Long id) {
        AgentResponse response = agentService.getById(id);
        return Result.success(response);
    }

    /**
     * 创建 Agent
     */
    @PostMapping
    @Operation(summary = "创建 Agent", description = "创建新的 AI Agent")
    public Result<AgentResponse> create(@Valid @RequestBody AgentCreateRequest request) {
        Agent agent = agentService.create(request);
        return Result.success(toResponse(agent));
    }

    /**
     * 更新 Agent
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新 Agent", description = "更新 Agent 配置")
    public Result<AgentResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody AgentUpdateRequest request
    ) {
        Agent agent = agentService.update(id, request);
        return Result.success(toResponse(agent));
    }

    /**
     * 删除 Agent
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除 Agent", description = "删除 Agent（逻辑删除）")
    public Result<Void> delete(@PathVariable Long id) {
        agentService.delete(id);
        return Result.success();
    }

    /**
     * 绑定 MCP 工具列表（全量替换）
     */
    @PutMapping("/{id}/tools")
    @Operation(summary = "绑定 MCP 工具", description = "全量替换 Agent 绑定的 MCP 工具列表，最多 10 个")
    public Result<Void> bindTools(
            @PathVariable Long id,
            @Valid @RequestBody BindToolsRequest request
    ) {
        agentService.bindTools(id, request);
        return Result.success();
    }

    /**
     * 测试对话
     */
    @PostMapping("/{id}/test-chat")
    @Operation(summary = "测试 Agent 对话", description = "发送测试消息验证 Agent 配置")
    public Result<AgentTestChatResponse> testChat(
            @PathVariable Long id,
            @Valid @RequestBody AgentTestChatRequest request
    ) {
        AgentTestChatResponse response = agentService.testChat(id, request);
        return Result.success(response);
    }

    /**
     * 实体转响应对象
     */
    private AgentResponse toResponse(Agent entity) {
        return AgentResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .avatar(entity.getAvatar())
                .defaultProviderId(entity.getDefaultProviderId())
                .defaultModel(entity.getDefaultModel())
                .systemPrompt(entity.getSystemPrompt())
                .userMessagePrefix(entity.getUserMessagePrefix())
                .welcomeMessage(entity.getWelcomeMessage())
                .temperature(entity.getTemperature())
                .maxTokens(entity.getMaxTokens())
                .topP(entity.getTopP())
                .frequencyPenalty(entity.getFrequencyPenalty())
                .presencePenalty(entity.getPresencePenalty())
                .maxHistoryRounds(entity.getMaxHistoryRounds())
                .streamEnabled(entity.getStreamEnabled())
                .agentConfig(entity.getAgentConfig())
                .enabled(entity.getEnabled())
                .knowledgeIds(entity.getKnowledgeIds())
                .mcpToolIds(entity.getMcpToolIds())
                .ragEnabled(entity.getRagEnabled())
                .ragTopK(entity.getRagTopK())
                .ragStrategy(entity.getRagStrategy())
                .createdAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null)
                .updatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null)
                .creatorId(entity.getCreatorId())
                .build();
    }
}
